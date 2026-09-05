package dev.devatlas.server.pipeline;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a candidate version string from a feed item's title (falling back to its feed-supplied
 * id), before that string is ever trusted with anything.
 *
 * <p>This is a best-effort heuristic over untrusted text, not a parser for a specification: release
 * feeds this pipeline is whitelisted against (GitHub {@code releases.atom} feeds in particular)
 * consistently title an entry with the tag name itself -- {@code "v20.1.0"}, {@code "4.1.1"} -- so
 * a short semantic-version-shaped pattern covers the real sources without inventing a bespoke
 * grammar per source.
 *
 * <p>Whatever is extracted here is still just a candidate. It is validated again, independently,
 * against the same {@code ^[A-Za-z0-9._+-]{1,64}$} pattern the REST contract requires (§5.7) at the
 * point it is about to be placed in a URL -- this class existing does not relax that requirement.
 */
public final class VersionExtractor {

  private static final Pattern VERSION_SHAPE =
      Pattern.compile("v?\\d+(?:\\.\\d+){1,3}(?:[-+][0-9A-Za-z.]+)?");
  private static final Pattern ALLOWED_CHARS = Pattern.compile("^[A-Za-z0-9._+-]{1,64}$");

  private VersionExtractor() {}

  public static Optional<String> extract(FeedItem item) {
    Optional<String> fromTitle = extractFrom(item.title());
    if (fromTitle.isPresent()) {
      return fromTitle;
    }
    return extractFrom(item.id());
  }

  private static Optional<String> extractFrom(String text) {
    if (text == null) {
      return Optional.empty();
    }
    Matcher matcher = VERSION_SHAPE.matcher(text);
    if (!matcher.find()) {
      return Optional.empty();
    }
    String candidate = matcher.group();
    if (!ALLOWED_CHARS.matcher(candidate).matches()) {
      return Optional.empty();
    }
    return Optional.of(candidate);
  }
}
