package dev.devatlas.server.pipeline;

/**
 * One entry of the verification chain, in execution order, as stored in {@code
 * source_updates.verify_checks} and echoed by the review-queue and source-update read endpoints
 * (§5.7). {@code check} is one of {@code SOURCE_WHITELISTED}, {@code VERSION_CONFIRMED}, {@code
 * HASH_NOT_SEEN} or {@code CONTENT_SANITY}.
 *
 * @param check the check's name
 * @param passed whether it passed
 * @param detail human-readable detail, or {@code null}
 */
public record VerifyCheckRecord(String check, boolean passed, String detail) {

  public static final String SOURCE_WHITELISTED = "SOURCE_WHITELISTED";
  public static final String VERSION_CONFIRMED = "VERSION_CONFIRMED";
  public static final String HASH_NOT_SEEN = "HASH_NOT_SEEN";
  public static final String CONTENT_SANITY = "CONTENT_SANITY";
}
