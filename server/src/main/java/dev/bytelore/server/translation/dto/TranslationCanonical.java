package dev.bytelore.server.translation.dto;

/** The canonical English text of a translatable entity (§5.6). */
public record TranslationCanonical(String locale, String title, String body) {

  public static TranslationCanonical of(String title, String body) {
    return new TranslationCanonical("en", title, body);
  }
}
