package dev.bytelore.server.content.packaging;

/**
 * One non-English translation of a lesson, nested inside a {@link LessonPackage} (content sync
 * protocol §4.3). Only locales that actually have a stored {@code ContentTranslation} row appear
 * here; a missing locale is not an error and not an empty entry, it is simply absent.
 *
 * <p>The component is named {@code body}, matching the wire key and the {@code (title, body)} shape
 * of the translation row it comes from. A lesson's own markdown is {@code body_markdown} and lives
 * at the top level of the package; the two are different fields and must not converge on one name.
 *
 * @param locale two-letter locale code, {@code tr}/{@code fr}/{@code de}
 * @param title translated title
 * @param body translated body, sanitized like the canonical body
 */
public record TranslationPackageItem(String locale, String title, String body) {}
