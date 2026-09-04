package dev.devatlas.server.content;

/**
 * Derives a blog post excerpt from its sanitized markdown body (§5.2.5): the first 280 characters
 * with markdown formatting stripped. {@code excerpt} is never an authored field -- it is always
 * computed, so a post can never carry a stale excerpt after its body is edited.
 */
final class ExcerptGenerator {

  private static final int MAX_LENGTH = 280;

  private ExcerptGenerator() {}

  static String excerpt(String bodyMarkdown) {
    if (bodyMarkdown == null) {
      return "";
    }
    String plain =
        bodyMarkdown
            .replaceAll("(?s)```.*?```", " ")
            .replaceAll("`([^`]*)`", "$1")
            .replaceAll("(?m)^#{1,6}\\s*", "")
            .replaceAll("!\\[([^]]*)]\\([^)]*\\)", "$1")
            .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")
            .replaceAll("[*_>#`]", "")
            .replaceAll("(?m)^-\\s+", "")
            .replaceAll("\\s+", " ")
            .trim();
    return plain.length() <= MAX_LENGTH ? plain : plain.substring(0, MAX_LENGTH);
  }
}
