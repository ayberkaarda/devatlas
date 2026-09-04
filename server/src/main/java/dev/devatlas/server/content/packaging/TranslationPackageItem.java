package dev.devatlas.server.content.packaging;

/**
 * One non-English translation of a lesson, nested inside a {@link LessonPackage} (content sync
 * protocol §4.3). Only locales that actually have a stored {@code ContentTranslation} row appear
 * here; a missing locale is not an error and not an empty entry, it is simply absent.
 *
 * @param locale two-letter locale code, {@code tr}/{@code fr}/{@code de}
 * @param title translated title
 * @param bodyMarkdown translated body, sanitized like the canonical body
 */
public record TranslationPackageItem(String locale, String title, String bodyMarkdown) {}
