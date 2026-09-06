package dev.bytelore.server.pipeline;

/**
 * One feed item skipped for this cycle because the check that would have decided it hit a transient
 * failure on the endpoint being asked (currently: {@code VERSION_CONFIRMED} answering {@code 403}
 * or {@code 429}), rather than a fact about the item itself.
 *
 * <p>Deliberately not a {@link Rejection}, and not reported in {@code rejections[]} of the manual
 * fetch response (§5.7): the item was not rejected, no {@code SourceUpdate} row was written for it,
 * and it is retried in full on the next fetch cycle rather than being remembered as decided.
 *
 * @param versionString the extracted version string, or {@code null} if none could be extracted
 * @param checkName the name of the check that deferred rather than failed
 * @param detail human-readable detail
 */
public record Deferral(String versionString, String checkName, String detail) {}
