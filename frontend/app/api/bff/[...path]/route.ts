// BFF proxy for client-side API calls. The browser calls /api/bff/<gateway-path>;
// this handler reads the httpOnly access cookie, forwards to the gateway with a Bearer
// header, and on 401 refreshes (setting new cookies) and retries once. Keeps tokens
// server-only and same-origin (no CORS/cookie-port issues).
import { cookies } from "next/headers";
import { NextResponse, type NextRequest } from "next/server";
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  GATEWAY,
  refreshTokens,
  setAuthCookies,
} from "@/lib/session/cookies";

async function handle(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  const target = `${GATEWAY}/${path.join("/")}${req.nextUrl.search}`;

  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const refresh = jar.get(REFRESH_COOKIE)?.value;

  // Forward the body as raw bytes so binary payloads (multipart image uploads) pass
  // through intact, and preserve the Content-Type (incl. the multipart boundary).
  const hasBody = req.method !== "GET" && req.method !== "HEAD";
  const rawBody = hasBody ? await req.arrayBuffer() : undefined;
  const contentType = req.headers.get("content-type");
  // Forward the client's idempotency key so a retried checkout dedupes at order-service.
  const idempotencyKey = req.headers.get("idempotency-key");
  // Forward the client address so the gateway can rate-limit per client rather than per BFF
  // instance. We can only pass on what reached us: a reverse proxy in front of this app has
  // to set X-Forwarded-For. With nothing in front, this header is absent and the gateway
  // falls back to this container's address — one shared bucket per storefront. The gateway
  // only believes the header from trusted peers anyway (see ClientIpResolver), so a browser
  // setting it directly achieves nothing.
  const forwardedFor = req.headers.get("x-forwarded-for");

  const send = (token?: string) =>
    fetch(target, {
      method: req.method,
      headers: {
        Accept: "application/json",
        ...(rawBody && rawBody.byteLength > 0 && contentType ? { "Content-Type": contentType } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {}),
        ...(forwardedFor ? { "X-Forwarded-For": forwardedFor } : {}),
      },
      body: rawBody && rawBody.byteLength > 0 ? rawBody : undefined,
      cache: "no-store",
    });

  let upstream = await send(access);
  let rotated: { accessToken: string; refreshToken: string } | null = null;

  if (upstream.status === 401 && refresh) {
    const tokens = await refreshTokens(refresh);
    if (tokens) {
      rotated = tokens;
      upstream = await send(tokens.accessToken);
    }
  }

  // Return the upstream bytes verbatim (works for JSON and any binary response).
  const body = await upstream.arrayBuffer();
  const res = new NextResponse(body.byteLength > 0 ? body : null, {
    status: upstream.status,
    headers: { "Content-Type": upstream.headers.get("content-type") ?? "application/json" },
  });
  if (rotated) setAuthCookies(res, rotated.accessToken, rotated.refreshToken);
  return res;
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
