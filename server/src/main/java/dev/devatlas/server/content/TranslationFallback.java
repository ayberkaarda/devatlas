package dev.devatlas.server.content;

import dev.devatlas.server.domain.UserLocale;

/**
 * The three fields every translatable object in a response carries (§2.7): the locale actually
 * served, the locale that was requested, and whether the served locale is a fallback to English.
 * Clients render the "not yet translated" badge from {@code isFallback} alone and never compare
 * locale strings themselves.
 */
public record TranslationFallback(String locale, String requestedLocale, boolean isFallback) {

  /**
   * The resolution for one translatable entity, given whether a translation row was found for the
   * requested locale. English requests never look anything up: the canonical columns are already
   * the answer.
   */
  public static TranslationFallback of(UserLocale requested, boolean translationFound) {
    if (requested == UserLocale.EN) {
      return new TranslationFallback("en", "en", false);
    }
    return translationFound
        ? new TranslationFallback(requested.code(), requested.code(), false)
        : new TranslationFallback("en", requested.code(), true);
  }

  /**
   * The resolution for a mind map, which is never translated in v1 (§5.2.4). The served locale is
   * always {@code en}; {@code isFallback} is true for any requested locale other than {@code en},
   * so clients need no special case beyond the one they already have for an untranslated entity.
   */
  public static TranslationFallback mindMap(UserLocale requested) {
    return new TranslationFallback("en", requested.code(), requested != UserLocale.EN);
  }
}
