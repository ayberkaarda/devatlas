/**
 * The host a source link points at, named the way a reader would name it.
 *
 * A post the ingest pipeline created always carries a link to what it
 * summarises — the pipeline refuses to publish one without it — and that
 * link is how a reader checks a claim rather than taking the summary on
 * trust. The host is what identifies it at a glance; a full URL in a list
 * row is mostly path noise, and the `www.` prefix distinguishes nothing.
 *
 * A value that does not parse as a URL is returned unchanged rather than
 * dropped. The link is rendered either way, and showing the stored string is
 * more honest than showing nothing where something was stored.
 */
export function sourceHost(url: string): string {
  let host: string;
  try {
    host = new URL(url).hostname;
  } catch {
    return url;
  }
  return host.startsWith('www.') ? host.slice(4) : host;
}
