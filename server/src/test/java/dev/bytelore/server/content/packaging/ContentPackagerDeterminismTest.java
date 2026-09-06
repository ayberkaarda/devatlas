package dev.bytelore.server.content.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import dev.bytelore.server.common.MarkdownSanitizer;
import dev.bytelore.server.common.TextNormalizer;
import dev.bytelore.server.domain.Difficulty;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The determinism guarantee content sync protocol §1 exists to make, exercised at the two layers
 * that jointly produce it: {@link TextNormalizer} (§3.1, the write-boundary routine) and {@link
 * ContentPackager}/{@link CanonicalJson} (§3.2, the canonical byte encoding). No Spring context is
 * needed -- both layers are plain, dependency-free code, which is the point: a determinism bug
 * should be catchable in milliseconds, not through a Testcontainers round trip.
 *
 * <p>These are the required tests of §3.5, save the two that need a live write path end to end
 * (same content twice through the real service, and "adding a translation bumps content_version"),
 * which live in {@code ContentVersionBumpCoverageIT} instead, next to the reflection-based coverage
 * check they share a fixture style with.
 */
class ContentPackagerDeterminismTest {

  private final ContentPackager packager = new ContentPackager();

  @Test
  void sameContentPackagedTwiceProducesAnIdenticalDigest() {
    LessonPackage pkg = sampleLesson("A signal is a value.");

    PackagedContent first = packager.packageLesson(pkg);
    PackagedContent second = packager.packageLesson(pkg);

    assertThat(first.sha256Hex()).isEqualTo(second.sha256Hex());
    assertThat(first.bytes()).isEqualTo(second.bytes());
  }

  @Test
  void textStoredAsCrlfAndAsLfNormalizeToIdenticalStoredTextThereforeIdenticalDigest() {
    String crlf = "Line one.\r\nLine two.\r\n";
    String lf = "Line one.\nLine two.\n";

    String storedFromCrlf = TextNormalizer.normalize(crlf);
    String storedFromLf = TextNormalizer.normalize(lf);
    assertThat(storedFromCrlf).isEqualTo(storedFromLf);

    PackagedContent a = packager.packageLesson(sampleLesson(storedFromCrlf));
    PackagedContent b = packager.packageLesson(sampleLesson(storedFromLf));
    assertThat(a.sha256Hex()).isEqualTo(b.sha256Hex());
  }

  @Test
  void
      textStoredWithAndWithoutAByteOrderMarkNormalizeToIdenticalStoredTextThereforeIdenticalDigest() {
    String bomChar = String.valueOf((char) 0xFEFF);
    String withBom = bomChar + "A signal is a value.";
    String withoutBom = "A signal is a value.";

    String storedWithBom = TextNormalizer.normalize(withBom);
    String storedWithoutBom = TextNormalizer.normalize(withoutBom);
    assertThat(storedWithBom).isEqualTo(storedWithoutBom);

    PackagedContent a = packager.packageLesson(sampleLesson(storedWithBom));
    PackagedContent b = packager.packageLesson(sampleLesson(storedWithoutBom));
    assertThat(a.sha256Hex()).isEqualTo(b.sha256Hex());
  }

  @Test
  void nfcAndNfdFormsNormalizeToIdenticalStoredTextThereforeIdenticalDigest() {
    // "Café": NFD is 'e' followed by a combining acute accent (U+0301); NFC is the single
    // precomposed code point U+00E9. Both render identically and must hash identically.
    String nfd = "Caf" + "é" + " is a value.";
    String nfc = "Caf" + "é" + " is a value.";

    String storedFromNfd = TextNormalizer.normalize(nfd);
    String storedFromNfc = TextNormalizer.normalize(nfc);
    assertThat(storedFromNfd).isEqualTo(storedFromNfc);

    PackagedContent a = packager.packageLesson(sampleLesson(storedFromNfd));
    PackagedContent b = packager.packageLesson(sampleLesson(storedFromNfc));
    assertThat(a.sha256Hex()).isEqualTo(b.sha256Hex());
  }

  /**
   * The write boundary's safety check is a predicate, not a transformation, so it cannot move a
   * digest. A body that an HTML serializer would have rewritten -- code fences, an equals sign, an
   * apostrophe, a blockquote -- must package to the same bytes whether it arrived with CRLF or LF
   * line endings, and to the same bytes twice in a row.
   */
  @Test
  void aBodyThatPassesTheSafetyCheckDigestsIdenticallyFromCrlfAndLfInput() {
    String lf =
        "# Notes\n\nDon't use `==`; a < b.\n\n```ts\nconst answer: number = 42;\n```\n\n> quoted\n";
    String crlf = lf.replace("\n", "\r\n");

    String storedFromLf = TextNormalizer.normalize(lf);
    String storedFromCrlf = TextNormalizer.normalize(crlf);

    // Both are admitted, and neither is altered by being admitted.
    MarkdownSanitizer.validateMarkdown(storedFromLf, "body_markdown");
    MarkdownSanitizer.validateMarkdown(storedFromCrlf, "body_markdown");
    assertThat(storedFromLf).isEqualTo(lf);
    assertThat(storedFromCrlf).isEqualTo(lf);

    String firstDigest = packager.packageLesson(sampleLesson(storedFromLf)).sha256Hex();
    String secondDigest = packager.packageLesson(sampleLesson(storedFromLf)).sha256Hex();
    String crlfDigest = packager.packageLesson(sampleLesson(storedFromCrlf)).sha256Hex();

    assertThat(firstDigest).isEqualTo(secondDigest).isEqualTo(crlfDigest);
  }

  @Test
  void twoTrailingSpacesOnALineSurviveNormalizationRatherThanBeingSilentlyStripped() {
    // Two trailing spaces are a Markdown hard line break. Stripping them would silently change
    // how the lesson renders -- a digest that is stable because it quietly corrupted the content
    // is worse than no digest at all (§3.1).
    String withHardBreak = "Line one.  \nLine two.\n";

    String stored = TextNormalizer.normalize(withHardBreak);

    assertThat(stored).contains("Line one.  \n");
  }

  @Test
  void objectsBuiltWithKeysInsertedInDifferentOrdersProduceIdenticalBytes() {
    Map<String, Object> first = new LinkedHashMap<>();
    first.put("b", 2);
    first.put("a", 1);
    first.put("c", List.of(Map.of("y", 2, "x", 1)));

    Map<String, Object> second = new LinkedHashMap<>();
    second.put("c", List.of(Map.of("x", 1, "y", 2)));
    second.put("a", 1);
    second.put("b", 2);

    assertThat(CanonicalJson.bytes(first)).isEqualTo(CanonicalJson.bytes(second));
  }

  @Test
  void canonicalBytesCarryNoInsignificantWhitespaceAndSortedTopLevelKeys() {
    LessonPackage pkg = sampleLesson("Body.");
    byte[] bytes = packager.packageLesson(pkg).bytes();
    String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

    assertThat(json).doesNotContain(": ").doesNotContain(", ").doesNotContain("\n");
    // entity_id sorts before entity_type, which sorts before slug -- confirms genuine code-point
    // key sorting rather than declaration order (the LessonPackage record does not declare fields
    // in this order).
    assertThat(json.indexOf("\"entity_id\"")).isLessThan(json.indexOf("\"entity_type\""));
    assertThat(json.indexOf("\"entity_type\"")).isLessThan(json.indexOf("\"slug\""));
  }

  @Test
  void aNullEstimatedMinutesIsAnAbsentKeyNotANullValue() {
    LessonPackage pkg =
        new LessonPackage(
            UUID.fromString("018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70"),
            LessonPackage.ENTITY_TYPE,
            1,
            "signals-basics",
            "Introduction to signals",
            "Body.",
            Difficulty.BEGINNER,
            null,
            UUID.fromString("018f3a02-4411-7f60-9c22-77b0a1e4cc90"),
            1,
            List.of(),
            List.of());

    byte[] bytes = packager.packageLesson(pkg).bytes();
    String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

    assertThat(json).doesNotContain("estimated_minutes").doesNotContain("null");
  }

  /**
   * The lesson's own markdown and a translation's text are two different fields with two different
   * names, and a package that used one name for both would be unparseable without knowing which
   * level it was reading. {@code body_markdown} belongs to the lesson, at the top level, and
   * appears exactly once; a translation entry carries {@code body}, the same name the translation
   * row, the read API and a track manifest all use.
   */
  @Test
  void aTranslationCarriesBodyWhileTheLessonsOwnMarkdownStaysBodyMarkdown() {
    byte[] bytes = packager.packageLesson(sampleLesson("# Signals\n")).bytes();
    String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

    // Canonical key order inside the translation entry: body, locale, title.
    assertThat(json)
        .contains(
            "{\"body\":\"Sinyaller hakkinda.\",\"locale\":\"tr\",\"title\":\"Sinyallere giris\"}");
    assertThat(json.split("\"body_markdown\"", -1).length - 1).isEqualTo(1);
  }

  private static LessonPackage sampleLesson(String bodyMarkdown) {
    return new LessonPackage(
        UUID.fromString("018f3b21-6c4a-7b0e-9d31-4a2f8c5e1b70"),
        LessonPackage.ENTITY_TYPE,
        12,
        "signals-basics",
        "Introduction to signals",
        bodyMarkdown,
        Difficulty.INTERMEDIATE,
        25,
        UUID.fromString("018f3a02-4411-7f60-9c22-77b0a1e4cc90"),
        3,
        List.of(
            new CodeExamplePackageItem(
                "typescript", "const count = signal(0);", "A writable signal", 1)),
        List.of(new TranslationPackageItem("tr", "Sinyallere giris", "Sinyaller hakkinda.")));
  }
}
