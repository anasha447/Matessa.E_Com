// Uploaded product images are served by commercial-service behind the gateway at /media/**.
// The browser never talks to the gateway: next.config.ts rewrites /media/* on this origin to
// the gateway server-side, so a relative /media/... URL works as-is (and the gateway need
// not be reachable from the internet). External URLs and local static assets are unchanged.
export function mediaUrl(url: string): string {
  return url;
}
