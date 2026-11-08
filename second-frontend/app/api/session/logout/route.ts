import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { GATEWAY, REFRESH_COOKIE, clearAuthCookies } from "@/lib/session/cookies";

/** Revoke the refresh token at the gateway and clear the session cookies. */
export async function POST() {
  const refresh = (await cookies()).get(REFRESH_COOKIE)?.value;
  if (refresh) {
    await fetch(`${GATEWAY}/api/auth/logout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: refresh }),
      cache: "no-store",
    }).catch(() => {});
  }
  const res = NextResponse.json({ ok: true });
  clearAuthCookies(res);
  return res;
}
