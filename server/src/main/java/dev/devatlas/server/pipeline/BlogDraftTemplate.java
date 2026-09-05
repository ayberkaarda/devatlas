package dev.devatlas.server.pipeline;

import dev.devatlas.server.domain.WhitelistSource;
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

  private static String excerpt(String content) {
    if (content == null || content.isBlank()) {
      return "No further details were included with this release.";
    }
    String collapsed = content.replaceAll("\\s+", " ").trim();
    if (collapsed.length() <= EXCERPT_MAX_CHARS) {
      return collapsed;
    }
    return collapsed.substring(0, EXCERPT_MAX_CHARS).trim() + "...";
  }
}
