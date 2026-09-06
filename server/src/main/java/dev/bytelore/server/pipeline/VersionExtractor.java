package dev.bytelore.server.pipeline;

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
 *
 * <p>The shape deliberately captures a suffix attached to the numeric core with <em>no</em>
 * separator at all (a bare letter directly after the last digit, e.g. {@code "3.15.0b2"}), not only
 * the {@code -}/{@code +} separated ones ({@code "-rc1"}, {@code "+14"}). An earlier version of
 * this pattern stopped at the numeric core and silently dropped such a suffix, which meant a
 * pre-release build could be extracted, matched and confirmed as if it were the stable release it
 * is a pre-release *of* -- the version-confirmation check has no way to notice a suffix that was
 * never handed to it. Capturing the whole thing here is what lets {@link #isPreRelease} see it.
 */
public final class VersionExtractor {

  private static final Pattern VERSION_SHAPE =
      Pattern.compile("v?\\d+(?:\\.\\d+){1,3}(?:[-+][0-9A-Za-z.]+|[A-Za-z][0-9A-Za-z.]*)?");
  private static final Pattern ALLOWED_CHARS = Pattern.compile("^[A-Za-z0-9._+-]{1,64}$");

  /** The numeric core alone, anchored at the start of an already-extracted candidate. */
  private static final Pattern NUMERIC_CORE = Pattern.compile("^v?\\d+(?:\\.\\d+){1,3}");

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

  /**
   * True if {@code candidate} carries anything at all after its numeric core -- a {@code -}/{@code
   * +} separated tag, a bare-letter suffix, or any other trailing character. The {@code
   * STABLE_RELEASE} check (§5.7) treats every one of those shapes as a pre-release, on the same
   * reasoning: a version string that is exactly its numeric core is the only shape a stable,
   * generally-available release is ever announced under across the whitelisted sources.
   *
   * @param candidate a string already produced by {@link #extract}; behaviour on anything else is
   *     unspecified
   */
  public static boolean isPreRelease(String candidate) {
    if (candidate == null) {
      return false;
    }
    Matcher core = NUMERIC_CORE.matcher(candidate);
    return core.find() && core.end() < candidate.length();
  }
}
