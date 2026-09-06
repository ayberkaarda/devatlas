package dev.bytelore.server.pipeline;

import dev.bytelore.server.common.MarkdownSanitizer;
import dev.bytelore.server.domain.WhitelistSource;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Templates a blog post draft from a verified feed item: title, version, highlights and the
 * mandatory source link. Pure and side-effect free -- it builds text, it does not persist anything
 * or decide whether the item should become a draft at all; that decision belongs to {@link
 * PipelineItemProcessor}.
 */
public final class BlogDraftTemplate {

  private static final Pattern NON_SLUG_CHARS = Pattern.compile("[^a-z0-9]+");
  private static final Pattern EDGE_HYPHENS = Pattern.compile("^-+|-+$");
  private static final int EXCERPT_MAX_CHARS = 800;

  private BlogDraftTemplate() {}

  /**
   * @param slugBase a slug candidate derived from the source name and version; the caller (which
   *     owns slug uniqueness) may still have to disambiguate it
   * @param title the drafted title
   * @param bodyMarkdown the drafted body, including the mandatory source link
   * @param sourceUrl the feed item's own link -- becomes {@code BlogPost.sourceUrl}
   */
  public record DraftContent(
      String slugBase, String title, String bodyMarkdown, String sourceUrl) {}

  public static DraftContent build(WhitelistSource source, FeedItem item, String versionString) {
    String title = "%s %s Released".formatted(source.getName(), versionString);
    String slugBase = slugify(source.getName() + "-" + versionString);
    String highlights = excerpt(item.content());

    String body =
        """
        %s has released **%s**.

        ## Highlights

        %s

        [View the original announcement](%s)
        """
            .formatted(source.getName(), versionString, highlights, item.link());

    return new DraftContent(slugBase, title, body, item.link());
  }

  private static String slugify(String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    String replaced = NON_SLUG_CHARS.matcher(lower).replaceAll("-");
    String trimmed = EDGE_HYPHENS.matcher(replaced).replaceAll("");
    return trimmed.isBlank() ? "release" : trimmed;
  }

  /**
   * Cuts a feed item's body down to a short excerpt that is safe to embed in a draft body.
   *
   * <p>Unlike an authored body, this input really is HTML -- it comes out of an Atom {@code
   * <content>} element -- so the sanitizer is used here as a transformer, which is what it is
   * designed for. Real release notes carry presentational attributes and wrapper elements the
   * server's allow-list does not admit; refusing an automated draft over them would leave a
   * rejection nobody can edit into shape, so they are stripped instead.
   *
   * <p><strong>This is the one place stored markdown is not what a human wrote.</strong> Everywhere
   * else the write boundary accepts or refuses a body and never alters it; here the excerpt is the
   * sanitizer's own output, so its entity rewriting comes with it -- an administrator opening the
   * draft sees {@code &#39;} where the feed had an apostrophe and {@code &#61;} where it had an
   * equals sign. The exception is deliberate and bounded: it applies only to automatically sourced
   * drafts, every such draft is read by a human before it can be published, and re-saving one
   * through the editor stores exactly what the reviewer leaves behind. The alternative -- holding
   * syndicated markup to the authored-content rule -- would refuse nearly every real release note
   * over tracking attributes nobody wrote on purpose.
   *
   * <p>It is sanitized <strong>twice</strong>, and the second pass is not redundant. Truncating
   * markup at a character count can cut a tag in half, and an unterminated tag swallows everything
   * after it -- including the mandatory source link the template appends below. Measured against
   * two real release feeds, that is not a corner case: eleven of twenty items were truncated
   * mid-tag and the resulting draft was refused by the allow-list check every draft is subjected to
   * before storage. The second pass discards the severed tag, and the policy's own output passes
   * the check unchanged, so a drafted item always survives the boundary it is about to cross.
   */
  private static String excerpt(String content) {
    if (content == null || content.isBlank()) {
      return "No further details were included with this release.";
    }
    String safe = MarkdownSanitizer.sanitizeHtml(content);
    String collapsed = safe.replaceAll("\\s+", " ").trim();
    if (collapsed.isBlank()) {
      return "No further details were included with this release.";
    }
    if (collapsed.length() <= EXCERPT_MAX_CHARS) {
      return collapsed;
    }
    String truncated = collapsed.substring(0, EXCERPT_MAX_CHARS).trim();
    return MarkdownSanitizer.sanitizeHtml(truncated).trim() + "...";
  }
}
