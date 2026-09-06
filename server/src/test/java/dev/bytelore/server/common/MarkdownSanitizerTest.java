package dev.bytelore.server.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.bytelore.server.domain.WhitelistSource;
import dev.bytelore.server.pipeline.BlogDraftTemplate;
import dev.bytelore.server.pipeline.FeedItem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The write boundary's safety rule, at the unit level: what the allow-list admits, what it refuses,
 * and -- the point of the whole exercise -- that admitting a body never alters it.
 *
 * <p>The allow-list is not a matter of taste, so it is not asserted against taste. It is measured
 * against the two bodies of content this server actually handles: the seeded lessons and
 * translations, and real GitHub release-notes HTML captured from two whitelisted feeds ({@code
 * fixtures/release-notes-github.html}). If a future change to the policy would reject either, these
 * tests say so before an author or the ingest pipeline finds out.
 */
class MarkdownSanitizerTest {

  // ---------------------------------------------------------------- admitted, and left alone

  @Test
  void aBodyFullOfTheCharactersAnHtmlSerializerRewritesIsAdmittedUntouched() {
    // Every character in here was measured to be entity-encoded by the OWASP serializer when the
    // markdown source was run through it: " & ' + < = > @ and the backtick. A body that survives
    // this test is a body an author can actually write.
    String body =
        """
        # Comparing values

        Don't use `==` when you mean `===`; a < b and b > a are both fine.

        ```ts
        const answer: number = 42;
        const greeting = `hi ${name}`;
        const ok = a === b && c !== d;
        ```

        > Quoted advice: "measure it" -- mail me@example.com if it differs.

        Inline `x = y + 1` and a stray & ampersand, plus a + sign.

        ---

        Trailing hard break here.\s\s
        Next line.
        """;
    String normalized = TextNormalizer.normalize(body);

    assertThatCode(() -> MarkdownSanitizer.validateMarkdown(normalized, "body_markdown"))
        .doesNotThrowAnyException();
    // The method returns nothing and mutates nothing; this is what "byte-preserving" means at the
    // unit level, and the round-trip integration tests assert the same thing through the API.
    assertThat(normalized)
        .contains("```ts")
        .contains("const answer: number = 42;")
        .contains("Don't use `==`")
        .contains("> Quoted advice: \"measure it\"")
        .contains("me@example.com")
        .contains("a < b and b > a")
        .contains("& ampersand")
        .contains("a + sign")
        .doesNotContain("&#")
        .doesNotContain("&lt;")
        .doesNotContain("&gt;")
        .doesNotContain("&amp;");
  }

  @Test
  void aScriptTagInsideAFenceIsCodeAndIsAdmitted() {
    // The check inspects the rendered HTML, and a fence renders as escaped text. A lesson that
    // teaches what a script tag looks like is not a lesson that contains one.
    assertThatCode(
            () ->
                MarkdownSanitizer.validateMarkdown(
                    "Never write this:\n\n```html\n<script>alert(1)</script>\n```\n", "body"))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "text with <b>bold</b> and <em>emphasis</em> inline",
        "<details><summary>Show the solution</summary>\n\nthe answer\n\n</details>",
        "| column | value |\n|---|---|\n| a | 1 |",
        "![diagram](https://example.test/d.png)",
        "[link](https://example.test/) and [mail](mailto:me@example.test)",
        "- item\n- item\n\n1. one\n2. two\n\n***\n",
        "```\nplain fence, no info string\n```",
        "```language-with-a-long-name+v2\nx\n```"
      })
  void theAllowListAdmitsWhatOrdinaryContentNeeds(String body) {
    assertThatCode(() -> MarkdownSanitizer.validateMarkdown(body, "body"))
        .doesNotThrowAnyException();
  }

  // ---------------------------------------------------------------- refused, with a reason

  @Test
  void aRawScriptTagIsRefusedAndNamedInTheMessage() {
    assertThatThrownBy(
            () -> MarkdownSanitizer.validateMarkdown("<script>alert(1)</script>", "body"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNSAFE_HTML))
        .hasMessageContaining("<script>");
  }

  /**
   * Nothing in the source text looks like HTML; the destinations are refused because the check
   * resolves them the way a browser would.
   *
   * <p>The last three cases are the reason this check exists separately from the sanitizer at all.
   * A numeric character reference that does not end in a semicolon is left alone by the CommonMark
   * spec and by the sanitizer's own URL policy, so the destination arrives at both as ordinary text
   * and is pronounced clean -- while a browser resolves it inside an attribute value and navigates
   * to a live {@code javascript:} URL. Measured: the semicolon-terminated spelling was refused and
   * the semicolon-less one was not. They have to be refused alike.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "[click me](javascript:alert(1))",
        "[click me](JaVaScRiPt:alert(1))",
        "[click me](vbscript:msgbox(1))",
        "[click me](data:text/html,<b>x</b>)",
        "[click me](&#106;avascript:alert(1))",
        "[click me](&#106avascript:alert(1))",
        "[click me](javascript&#58alert(1))",
        "[click me](&#x6a;avascript:alert(1))",
        "[click me](<java script:alert(1)>)",
        "[click me][ref]\n\n[ref]: javascript:alert(1)"
      })
  void aDestinationThatWouldNavigateSomewhereElseIsRefused(String body) {
    assertThatThrownBy(() -> MarkdownSanitizer.validateMarkdown(body, "body"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNSAFE_HTML))
        .hasMessageContaining("scheme");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[ok](https://example.test/a?b=1&c=2)",
        "[ok](http://example.test/)",
        "[ok](mailto:me@example.test)",
        "[ok](/relative/path)",
        "[ok](./sibling?q=&#106)",
        "[ok](#anchor)",
        "<https://example.test/auto>",
        "![ok](https://example.test/i.png)"
      })
  void anOrdinaryDestinationIsAdmitted(String body) {
    assertThatCode(() -> MarkdownSanitizer.validateMarkdown(body, "body"))
        .doesNotThrowAnyException();
  }

  /**
   * Raw markup declarations are refused before the sanitizer is consulted, because the sanitizer
   * cannot see them.
   *
   * <p>Its change listener reports a discarded tag or attribute; text it consumed as a comment was
   * never a tag, so it reports nothing. Its comment lexer also does not implement the HTML5
   * parser's comment-ending rules, so {@code <!-->}, {@code <!--->} and {@code --!>} open a comment
   * it never closes -- and everything after them, a script element included, is swallowed and
   * pronounced clean. A browser, and both markdown renderers in this system, end the comment where
   * HTML5 says and run what follows. Measured: every case here returned a clean verdict from the
   * policy alone.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "<!-->\n<script>alert(1)</script>\n-->",
        "<!--->\n<script>alert(1)</script>\n-->",
        "<!-- x --!>\n<script>alert(1)</script>\n-->",
        "<!DOCTYPE html>\n<script>alert(1)</script>",
        "<![CDATA[<script>alert(1)</script>]]>",
        "<?php echo 1; ?>",
        "prose with an <!-- ordinary comment --> in it"
      })
  void rawMarkupDeclarationsAreRefusedOutright(String body) {
    assertThatThrownBy(() -> MarkdownSanitizer.validateMarkdown(body, "body"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNSAFE_HTML))
        .hasMessageContaining("comment or declaration");
  }

  /**
   * Pathological nesting is answered quickly and as a refusal, not as a stack overflow or a
   * multi-second CPU burn. A body may be 200 000 characters, so without a pinned limit any
   * authenticated editor could spend the request thread at will.
   */
  @ParameterizedTest
  @ValueSource(strings = {"comment-openers", "emphasis-markers", "open-brackets", "blockquotes"})
  void aPathologicalBodyIsRefusedOrParsedQuicklyRatherThanConsumingTheThread(String shape) {
    String body =
        switch (shape) {
          case "comment-openers" -> "x <!--".repeat(20_000);
          case "emphasis-markers" -> "*".repeat(20_000);
          case "open-brackets" -> "[".repeat(20_000) + "x](https://example.test)";
          default -> "> ".repeat(20_000) + "x";
        };

    long startedAt = System.nanoTime();
    try {
      MarkdownSanitizer.validateMarkdown(body, "body");
    } catch (ApiException e) {
      assertThat(e.code()).isEqualTo(ErrorCode.UNSAFE_HTML);
    }
    long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;

    // Generous by two orders of magnitude against what was measured, so the assertion catches a
    // reintroduced super-linear parse rather than a slow build agent.
    assertThat(elapsedMs).as("%s took %d ms", shape, elapsedMs).isLessThan(2_000);
  }

  @Test
  void anEventHandlerAttributeIsRefusedAndTheAttributeIsNamed() {
    assertThatThrownBy(
            () ->
                MarkdownSanitizer.validateMarkdown(
                    "<img src=x onerror=\"alert(2)\">", "body_markdown"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNSAFE_HTML))
        .hasMessageContaining("img[onerror]")
        .hasMessageContaining("body_markdown");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "<iframe src=\"https://evil.test\"></iframe>",
        "<style>body{display:none}</style>",
        "<object data=\"x.swf\"></object>",
        "<embed src=\"x.swf\">",
        "<font color=\"red\">legacy</font>",
        "[data url](data:text/html;base64,PHNjcmlwdD4=)"
      })
  void theAllowListRefusesEverythingOutsideIt(String body) {
    assertThatThrownBy(() -> MarkdownSanitizer.validateMarkdown(body, "body"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNSAFE_HTML));
  }

  @Test
  void theRefusalMessageIsStableSoItCanBeAssertedOnAndLogged() {
    String body = "<script>a</script><style>b</style><img src=x onerror=y>";
    String first = messageOf(body);
    String second = messageOf(body);
    assertThat(first).isEqualTo(second).contains("<script>", "<style>", "img[onerror]");
  }

  private static String messageOf(String body) {
    try {
      MarkdownSanitizer.validateMarkdown(body, "body");
      throw new AssertionError("expected a refusal");
    } catch (ApiException e) {
      return e.getMessage();
    }
  }

  // ---------------------------------------------------------------- plain text (mind-map labels)

  @ParameterizedTest
  @ValueSource(strings = {"a < b", "5 > 3", "x = y", "a&b", "O'Reilly", "\"quoted\"", "a@b.test"})
  void aLabelThatIsOnlyPunctuationIsPlainTextAndPasses(String label) {
    assertThatCode(() -> MarkdownSanitizer.validatePlainText(label, "label"))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"a <b", "<b>bold</b>", "<script>x</script>"})
  void aLabelAnHtmlParserReadsAsATagIsRefused(String label) {
    // "a <b" is a legitimate label that is now refused; the message names the element so the author
    // knows a space fixes it. That trade is deliberate: a label is interpolated, never rendered as
    // markup, so the boundary asserts there is no markup rather than deleting some.
    assertThatThrownBy(() -> MarkdownSanitizer.validatePlainText(label, "label"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNSAFE_HTML))
        .hasMessageContaining("<");
  }

  // ---------------------------------------------------------------- the allow-list, measured

  @ParameterizedTest
  @ValueSource(
      strings = {"db/migration/V5__seed_content.sql", "db/migration/V9__seed_translation.sql"})
  void everySeededBodyIsAdmittedByTheAllowList(String migration) throws IOException {
    String sql = Files.readString(Path.of("src/main/resources", migration), StandardCharsets.UTF_8);
    Matcher literals = Pattern.compile("'((?:[^']|'')*)'", Pattern.DOTALL).matcher(sql);
    int checked = 0;
    while (literals.find()) {
      String value = literals.group(1).replace("''", "'");
      if (value.length() < 80) {
        continue; // identifiers, slugs and timestamps, not prose
      }
      checked++;
      String normalized = TextNormalizer.normalize(value);
      assertThatCode(() -> MarkdownSanitizer.validateMarkdown(normalized, "body_markdown"))
          .as("seeded body in %s", migration)
          .doesNotThrowAnyException();
    }
    assertThat(checked).as("prose literals found in %s", migration).isPositive();
  }

  @Test
  void realReleaseNotesHtmlSurvivesThePipelinePathAndPassesTheValidatorItThenFaces()
      throws IOException {
    String html = readFixture("fixtures/release-notes-github.html");

    // Straight through the validator it is refused -- syndicated markup carries tracking and
    // presentation attributes the allow-list does not admit. That is the expected answer for a
    // paste, and the reason the pipeline transforms its excerpt instead of validating it.
    assertThatThrownBy(() -> MarkdownSanitizer.validateMarkdown(html, "body_markdown"))
        .isInstanceOf(ApiException.class);

    // Through the pipeline's own path it passes, which is what has to hold: a drafted post is
    // subjected to the same validator every authored body is.
    WhitelistSource source = new WhitelistSource();
    source.setName("Spring Boot");
    FeedItem item =
        new FeedItem(
            "id-1", "Spring Boot 4.0.1", "https://example.test/r/4.0.1", html, Instant.EPOCH);
    String draft = BlogDraftTemplate.build(source, item, "4.0.1").bodyMarkdown();

    assertThatCode(
            () ->
                MarkdownSanitizer.validateMarkdown(
                    TextNormalizer.normalize(draft), "body_markdown"))
        .doesNotThrowAnyException();
    // The excerpt is truncated, and truncation is what used to cut a tag in half and swallow the
    // rest of the draft. The mandatory source link has to still be there.
    assertThat(draft).contains("[View the original announcement](https://example.test/r/4.0.1)");
  }

  @Test
  void theTransformerStripsWhatTheValidatorWouldHaveRefused() throws IOException {
    String sanitized =
        MarkdownSanitizer.sanitizeHtml(readFixture("fixtures/release-notes-github.html"));
    assertThat(sanitized)
        .doesNotContain("data-octo-click")
        .doesNotContain("data-hovercard-type")
        .doesNotContain("data-canonical-src")
        .doesNotContain("style=")
        .contains("<h2>")
        .contains("href=\"https://github.com/");
  }

  @Test
  void theTransformersOutputIsAcceptedByTheValidatorUnchanged() {
    // The pipeline depends on this: what the transformer emits must be admissible, or an automated
    // draft would be refused at a boundary nobody can edit it past.
    String once =
        MarkdownSanitizer.sanitizeHtml(
            "<p><a href=\"https://x.test\" rel=\"nofollow\" title=\"t\" class=\"c\">x</a></p>");
    assertThatCode(() -> MarkdownSanitizer.validateMarkdown(once, "body"))
        .doesNotThrowAnyException();
    assertThat(MarkdownSanitizer.sanitizeHtml(once)).isEqualTo(once);
  }

  @Test
  void aNullOrEmptyValueIsNotThisChecksBusiness() {
    assertThatCode(
            () -> {
              MarkdownSanitizer.validateMarkdown(null, "body");
              MarkdownSanitizer.validateMarkdown("", "body");
              MarkdownSanitizer.validatePlainText(null, "label");
              MarkdownSanitizer.validatePlainText("", "label");
            })
        .doesNotThrowAnyException();
    assertThat(MarkdownSanitizer.sanitizeHtml(null)).isNull();
  }

  private static String readFixture(String name) throws IOException {
    try (InputStream in = MarkdownSanitizerTest.class.getClassLoader().getResourceAsStream(name)) {
      assertThat(in).as("fixture %s", name).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void theFixtureIsRealSyndicatedHtmlAndNotAToy() throws IOException {
    String html = readFixture("fixtures/release-notes-github.html");
    assertThat(html.length()).isGreaterThan(3000);
    assertThat(List.of("data-hovercard-url", "data-canonical-src", "style=", "<table>"))
        .allSatisfy(marker -> assertThat(html).contains(marker));
  }
}
