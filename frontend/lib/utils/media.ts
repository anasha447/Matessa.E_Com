// Uploaded product images are served by the backend gateway at /media/**, not the Next
// origin. Prefix those with the public API base so <img src> resolves. External URLs and
// external and local static assets are returned unchanged.
const API_BASE = (process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:9080").replace(/\/+$/, "");

export function mediaUrl(url: string): string {
  if (!url) return url;
  if (url.startsWith("/media/")) return API_BASE + url;
  return url;
}
