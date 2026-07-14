import type { NextConfig } from "next";

// Server-side gateway base (same resolution as lib/session/cookies.ts).
const GATEWAY =
  process.env.API_GATEWAY_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:9080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      // Uploaded product images are stored as relative paths (/media/products/*.webp)
      // and served by commercial-service behind the gateway. The browser only sees the
      // frontend origin (:3000), so proxy /media/* to the gateway — otherwise <img> 404s
      // and shows alt text. Target is env-configurable for prod.
      { source: "/media/:path*", destination: `${GATEWAY}/media/:path*` },
    ];
  },
};

export default nextConfig;
