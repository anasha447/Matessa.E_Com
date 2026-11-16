import { NextResponse, type NextRequest } from "next/server";
import { GATEWAY, setAuthCookies, STORE_HEADERS } from "@/lib/session/cookies";
import { clientIp, rejectCrossSite } from "@/lib/server/guard";

/** Exchange email/password for a session: calls the gateway and stores the token pair
 *  in httpOnly cookies. Never returns the tokens to the client. */
export async function POST(req: NextRequest) {
  const blocked = rejectCrossSite(req); // login-CSRF / cross-site form posts
  if (blocked) return blocked;
  const ip = clientIp(req);
  const ipHeader: Record<string, string> = ip ? { "X-Forwarded-For": ip } : {};
  const { email, password } = await req.json().catch(() => ({}));
  if (!email || !password) {
    return NextResponse.json({ message: "Email and password are required." }, { status: 400 });
  }

  const upstream = await fetch(`${GATEWAY}/api/auth/login`, {
    method: "POST",
    headers: { ...STORE_HEADERS, ...ipHeader, "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
    cache: "no-store",
  });

  if (!upstream.ok) {
    const body = await upstream.json().catch(() => ({ message: "Invalid email or password." }));
    return NextResponse.json(body, { status: upstream.status });
  }

  const tokens = await upstream.json();

  // Fetch the user with the fresh token so the client can route by role
  // (staff → /admin, customer → /account) and enforce a first-login password change.
  // Non-fatal: if it fails, the client falls back to customer routing.
  let roles: string[] = [];
  let mustChangePassword = false;
  try {
    const me = await fetch(`${GATEWAY}/api/users/me`, {
      headers: { ...STORE_HEADERS, Authorization: `Bearer ${tokens.accessToken}` },
      cache: "no-store",
    });
    if (me.ok) {
      const u = await me.json();
      roles = Array.isArray(u.roles) ? u.roles : [];
      mustChangePassword = !!u.mustChangePassword;
    }
  } catch {
    /* ignore — routing falls back to customer default */
  }

  const res = NextResponse.json({ ok: true, roles, mustChangePassword });
  setAuthCookies(res, tokens.accessToken, tokens.refreshToken);
  return res;
}
