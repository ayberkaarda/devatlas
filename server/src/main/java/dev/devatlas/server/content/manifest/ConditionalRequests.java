package dev.devatlas.server.content.manifest;

/**
 * Matching of {@code If-None-Match} and {@code If-Match} header values against a strong entity tag,
 * per RFC 9110 §8.8.3 and §13.1.
 *
 * <p>Spring can answer {@code If-None-Match} on its own for a {@code ResponseEntity} that carries
 * an {@code ETag}, but not {@code If-Match}, and the download engine's resume path depends on the
 * second one: a {@code Range} request carries {@code If-Match: "<sha256>"} so that content
 * republished between two attempts produces a {@code 412} rather than a partial file spliced
 * together out of two different versions (§7). Both are therefore evaluated here, explicitly, so
 * that one reading of the header governs both directions.
 */
final class ConditionalRequests {

  private ConditionalRequests() {}

  /**
   * True when a comma-separated list of entity tags contains {@code etag}, or is the wildcard.
   *
   * <p>Comparison is weak per RFC 9110 for {@code If-None-Match} and strong for {@code If-Match};
   * the distinction cannot arise here, because every tag this API emits is strong -- it identifies
   * exact bytes -- so a {@code W/} prefix on an incoming value is stripped and the remainder
   * compared. Whitespace around list members is ignored.
   *
   * @param headerValue the raw header, or {@code null} when the header was absent
   * @param etag the quoted strong tag of the current representation
   */
  static boolean matches(String headerValue, String etag) {
    if (headerValue == null) {
      return false;
    }
    String trimmed = headerValue.trim();
    if (trimmed.isEmpty()) {
      return false;
    }
    if ("*".equals(trimmed)) {
      return true;
    }
    for (String candidate : trimmed.split(",")) {
      String value = candidate.trim();
      if (value.startsWith("W/")) {
        value = value.substring(2).trim();
      }
      if (value.equals(etag)) {
        return true;
      }
    }
    return false;
  }
}
