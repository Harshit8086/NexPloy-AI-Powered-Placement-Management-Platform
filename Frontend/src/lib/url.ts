/**
 * Only ever render/open URLs whose scheme is https. Anything else — http, javascript:, data:,
 * or an unparseable value — is dropped rather than trusted. Mirrors the guard used on the Resume
 * Intelligence page so every surface that renders externally-sourced links (YouTube enrichment,
 * curated resources) applies the same check before treating a URL as safe.
 */
export function isSafeHttpUrl(url: string): boolean {
  try {
    return new URL(url).protocol === "https:";
  } catch {
    return false;
  }
}
