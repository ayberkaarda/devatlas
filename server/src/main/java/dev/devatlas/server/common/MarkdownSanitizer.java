package dev.devatlas.server.common;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * The single place untrusted markdown and mind-map label text is sanitized before it is stored
 * (§2.8 of the REST contract).
 *
 * <p>Markdown bodies may carry inline HTML -- a common Markdown feature -- so they are run through
 * a conservative allow-list assembled from the sanitizer's predefined policies: block structure,
 * inline formatting, links (restricted to {@code http}/{@code https}/{@code mailto} by the library
 * itself), images and tables. Nothing here permits {@code <script>}, {@code <style>}, an
 * event-handler attribute, a {@code javascript:}/{@code data:} URL, or {@code <iframe>}/{@code
 * <object>}/{@code <embed>}; those are refused simply by never appearing on the allow-list, which
 * is how an allow-list sanitizer works -- there is no separate deny-list to keep in sync.
 *
 * <p>Mind-map labels are not prose: they are sanitized to plain text, stripping every tag and
 * keeping only the text content, because a node label is a short string, not a document.
 *
 * <p>Callers run {@link TextNormalizer#normalize(String)} first and sanitize the normalized value,
 * so that the bytes which end up stored, hashed and served are all the same bytes -- sanitizing an
 * un-normalized string and normalizing afterward would risk the sanitizer's own output diverging
 * from what {@link TextNormalizer} would have produced from the same input.
 */
public final class MarkdownSanitizer {

  private static final PolicyFactory MARKDOWN_POLICY =
      Sanitizers.BLOCKS
          .and(Sanitizers.FORMATTING)
          .and(Sanitizers.LINKS)
          .and(Sanitizers.IMAGES)
          .and(Sanitizers.TABLES);

  /** No element is on the allow-list, so every tag is stripped and only text content survives. */
  private static final PolicyFactory PLAIN_TEXT_POLICY = new HtmlPolicyBuilder().toFactory();

  private MarkdownSanitizer() {}

  /** Sanitizes a markdown body: a lesson, a blog post, or a track/module translation body. */
  public static String sanitizeMarkdown(String value) {
    if (value == null) {
      return null;
    }
    return MARKDOWN_POLICY.sanitize(value);
  }

  /** Sanitizes a mind-map node label, or any other field that must end up as plain text. */
  public static String sanitizePlainText(String value) {
    if (value == null) {
      return null;
    }
    return PLAIN_TEXT_POLICY.sanitize(value);
  }
}
