package dev.bytelore.server.pipeline;

/**
 * One entry of the verification chain, in execution order, as stored in {@code
 * source_updates.verify_checks} and echoed by the review-queue and source-update read endpoints
 * (§5.7). {@code check} is one of {@code SOURCE_WHITELISTED}, {@code VERSION_CONFIRMED}, {@code
 * HASH_NOT_SEEN}, {@code CONTENT_SANITY} or {@code DRAFT_VALIDATION}.
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

  /**
   * The drafted body was offered to the same allow-list an authored body faces, and admitted.
   *
   * <p>It is a verification check rather than an exception on purpose. A feed item can carry markup
   * that survives the transformer as text and is then refused once it sits in a markdown body -- a
   * security advisory quoting a {@code javascript:} link does exactly that. If the refusal escaped
   * as an exception it would roll back the item's own source-update row and its audit entry,
   * abandon every later item in the same feed, and leave the source's fetch timestamp untouched, so
   * the next cycle would fail identically with nothing in the audit trail to say why. Recorded
   * here, the item is rejected the way any other failed check rejects one: visibly, in the review
   * queue, with the rest of the feed still processed.
   */
  public static final String DRAFT_VALIDATION = "DRAFT_VALIDATION";
}
