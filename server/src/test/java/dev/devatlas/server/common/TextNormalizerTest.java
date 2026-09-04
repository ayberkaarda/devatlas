package dev.devatlas.server.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;
import org.junit.jupiter.api.Test;

/**
 * The normalization routine, tested directly rather than through a write endpoint.
 *
 * <p>These cases are the contract. Each one names a way that identical-looking content could
 * otherwise produce two different stored strings and therefore two different digests -- and one
 * case names the opposite risk, a rule that would make digests stable by quietly changing what the
 * author wrote.
 */
class TextNormalizerTest {

  private static final String BOM = "\uFEFF";

  @Test
  void stripsALeadingByteOrderMark() {
    assertThat(TextNormalizer.normalize(BOM + "# Title")).isEqualTo("# Title");
  }

  @Test
  void leavesAByteOrderMarkThatIsNotLeadingAlone() {
    // Only a leading mark is an encoding artefact; anywhere else it is a character the author
    // typed.
    assertThat(TextNormalizer.normalize("a" + BOM + "b")).isEqualTo("a" + BOM + "b");
  }

  @Test
  void convertsWindowsLineEndingsToLineFeeds() {
    assertThat(TextNormalizer.normalize("one\r\ntwo\r\n")).isEqualTo("one\ntwo\n");
  }

  @Test
  void convertsLoneCarriageReturnsToLineFeeds() {
    assertThat(TextNormalizer.normalize("one\rtwo")).isEqualTo("one\ntwo");
  }

  @Test
  void doesNotCollapseALineFeedFollowedByACarriageReturn() {
    assertThat(TextNormalizer.normalize("one\n\rtwo")).isEqualTo("one\n\ntwo");
  }

  /** The same text authored on two platforms has to end up as the same stored string. */
  @Test
  void crlfAndLfProduceIdenticalStoredText() {
    String windows = "## Heading\r\n\r\nBody line one.\r\nBody line two.\r\n";
    String unix = "## Heading\n\nBody line one.\nBody line two.\n";

    assertThat(TextNormalizer.normalize(windows)).isEqualTo(TextNormalizer.normalize(unix));
  }

  @Test
  void bomAndNoBomProduceIdenticalStoredText() {
    assertThat(TextNormalizer.normalize(BOM + "Body")).isEqualTo(TextNormalizer.normalize("Body"));
  }

  /** An accented character can be one code point or two; both render identically. */
  @Test
  void composedAndDecomposedFormsProduceIdenticalStoredText() {
    String composed = "Reaktivit\u00e9";
    String decomposed = "Reaktivite\u0301";
    assertThat(composed).isNotEqualTo(decomposed);

    assertThat(TextNormalizer.normalize(decomposed)).isEqualTo(TextNormalizer.normalize(composed));
    assertThat(Normalizer.isNormalized(TextNormalizer.normalize(decomposed), Normalizer.Form.NFC))
        .isTrue();
  }

  /**
   * The case that constrains the routine most.
   *
   * <p>Two trailing spaces are a hard line break in Markdown. Trimming them would make digests
   * beautifully stable while silently changing how a lesson renders -- a digest that is stable
   * because it corrupted the content is worse than no digest.
   */
  @Test
  void keepsTrailingSpacesThatFormAMarkdownHardLineBreak() {
    String withHardBreak = "first line  \nsecond line";

    assertThat(TextNormalizer.normalize(withHardBreak)).isEqualTo(withHardBreak);
  }

  @Test
  void keepsTrailingSpacesAfterLineEndingConversion() {
    assertThat(TextNormalizer.normalize("first line  \r\nsecond"))
        .isEqualTo("first line  \nsecond");
  }

  @Test
  void keepsTrailingNewlines() {
    assertThat(TextNormalizer.normalize("body\n\n")).isEqualTo("body\n\n");
  }

  @Test
  void keepsLeadingAndInteriorWhitespace() {
    String indented = "    indented code block\n\tand a tab\n";

    assertThat(TextNormalizer.normalize(indented)).isEqualTo(indented);
  }

  @Test
  void passesNullAndEmptyThrough() {
    assertThat(TextNormalizer.normalize(null)).isNull();
    assertThat(TextNormalizer.normalize("")).isEmpty();
  }

  @Test
  void isIdempotent() {
    String once = TextNormalizer.normalize(BOM + "Cafe\u0301\r\nlatte\u0301\r\n");

    assertThat(TextNormalizer.normalize(once)).isEqualTo(once);
  }

  /**
   * Code gets line endings normalized and nothing else. Recomposing a source listing would rewrite
   * an author's exact bytes -- a string literal, an identifier, a test fixture -- into something
   * they did not write.
   */
  @Test
  void codeNormalizationConvertsLineEndingsButNotUnicodeForm() {
    String decomposed = "const cafe\u0301 = 1;\r\n";

    String result = TextNormalizer.normalizeCode(decomposed);

    assertThat(result).isEqualTo("const cafe\u0301 = 1;\n");
    assertThat(result).isNotEqualTo(TextNormalizer.normalize(decomposed));
  }

  @Test
  void codeNormalizationStripsALeadingByteOrderMark() {
    assertThat(TextNormalizer.normalizeCode(BOM + "fn main() {}")).isEqualTo("fn main() {}");
  }

  @Test
  void codeNormalizationKeepsTrailingWhitespace() {
    assertThat(TextNormalizer.normalizeCode("let x = 1;   \n")).isEqualTo("let x = 1;   \n");
  }
}
