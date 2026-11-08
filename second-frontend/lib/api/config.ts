// Base URL for the API client. Server Components hit the gateway directly (public
// catalog reads need no auth); the browser routes through the same-origin BFF proxy
// (/api/bff) so the httpOnly session cookie authenticates owner-only calls without
// exposing tokens to client JS.
const GATEWAY = (
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:9080"
).replace(/\/+$/, "");

export const API_BASE_URL = typeof window === "undefined" ? GATEWAY : "/api/bff";
