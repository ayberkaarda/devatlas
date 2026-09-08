package dev.bytelore.server.corpus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.bytelore.server.corpus.ParsedCorpus.ParsedCodeExample;
import dev.bytelore.server.corpus.ParsedCorpus.ParsedLesson;
import dev.bytelore.server.corpus.ParsedCorpus.ParsedModule;
import dev.bytelore.server.corpus.ParsedCorpus.ParsedTrack;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * What the corpus loader accepts, and -- at much greater length -- what it refuses.
 *
 * <p>The refusal half is the point of the class. A validation layer nobody has watched refuse
 * anything is indistinguishable from one that does nothing, and seed content had never crossed a
 * write boundary at all before this loader existed, so "the corpus is validated" is a claim that
 * has to be demonstrated rather than asserted. Every refusal case here starts from a corpus that
 * loads cleanly in {@link #loadsTheFixtureCorpus()} and changes one value.
 */
class CorpusParserTest {

  private static final String TRACK_JSON = "fixture-path/track.json";
  private static final String A_BODY = "fixture-path/lessons/what-a-value-holds.md";
  private static final String A_METADATA = "fixture-path/lessons/what-a-value-holds.json";

  @Test
  void loadsTheFixtureCorpus() {
    ParsedCorpus corpus = CorpusParser.parse(CorpusFixture.valid());

    assertThat(corpus.tracks()).hasSize(1);
    ParsedTrack track = corpus.tracks().getFirst();
    assertThat(track.id()).isEqualTo(UUID.fromString("019205a0-1000-7000-8000-000000000032"));
    assertThat(track.slug()).isEqualTo("fixture-path");
    assertThat(track.modules()).hasSize(3);
    assertThat(track.modules().stream().map(ParsedModule::displayOrder)).containsExactly(1, 2, 3);
    assertThat(lessons(track)).hasSize(9);
    assertThat(listings(track)).hasSize(18);
    assertThat(track.mindMapId())
        .isEqualTo(UUID.fromString("019205a0-4000-7000-8000-000000000032"));
  }

  /**
   * The pinned version a track teaches ends up in the stored description, because the corpus format
   * puts it there rather than adding a column for one sentence.
   */
  @Test
  void theTaughtVersionBecomesTheDescriptionsTrailingSentence() {
    ParsedTrack track = CorpusParser.parse(CorpusFixture.valid()).tracks().getFirst();

    assertThat(track.description()).endsWith("Teaches Python 3.13.");
  }

  /** Bodies and listings arrive normalized, which is what makes a digest over them reproducible. */
  @Test
  void bodiesAndListingsArriveNormalized() {
    ParsedTrack track = CorpusParser.parse(CorpusFixture.valid()).tracks().getFirst();

    for (ParsedLesson lesson : lessons(track)) {
      assertThat(lesson.bodyMarkdown()).doesNotContain("\r");
      assertThat(lesson.bodyMarkdown()).doesNotStartWith("﻿");
    }
    for (ParsedCodeExample listing : listings(track)) {
      assertThat(listing.code()).doesNotContain("\r");
    }
  }

  /**
   * A checkout that produced CRLF and one that produced LF have to load to the same bytes. The
   * digest is computed over what is stored, so if this were not true the same lesson would hash
   * differently depending on which machine last touched the repository -- and the mismatch would
   * only surface on the platform that did not produce the manifest.
   */
  @Test
  void aCorpusCheckedOutWithCarriageReturnsLoadsToTheSameBytes() {
    TreeMap<String, byte[]> windowsCheckout = CorpusFixture.copy();
    windowsCheckout.replaceAll(
        (path, content) ->
            CorpusFixture.bytes(
                new String(content, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\n", "\r\n")));

    List<String> fromLf =
        lessons(CorpusParser.parse(CorpusFixture.valid()).tracks().getFirst()).stream()
            .map(ParsedLesson::bodyMarkdown)
            .toList();
    List<String> fromCrLf =
        lessons(CorpusParser.parse(windowsCheckout).tracks().getFirst()).stream()
            .map(ParsedLesson::bodyMarkdown)
            .toList();

    assertThat(fromCrLf).isEqualTo(fromLf);
  }

  /** Expected-output files are evidence for the verification runner, never content. */
  @Test
  void expectedOutputFilesAreNeitherStoredNorTreatedAsStray() {
    assertThat(CorpusFixture.valid())
        .containsKey("fixture-path/examples/what-a-value-holds-1.expected");

    ParsedTrack track = CorpusParser.parse(CorpusFixture.valid()).tracks().getFirst();

    assertThat(listings(track))
        .allSatisfy(listing -> assertThat(listing.language()).isNotEqualTo("expected"));
    assertThat(listings(track)).hasSize(18);
  }

  /** The mind map says what the structure says, and it is derived rather than read. */
  @Test
  void theMindMapIsDerivedFromTheTrackStructure() {
    ParsedTrack track = CorpusParser.parse(CorpusFixture.valid()).tracks().getFirst();

    assertThat(track.mindMapRoot()).contains("\"id\":\"track\"", "\"label\":\"The Fixture Path\"");
    assertThat(track.mindMapRoot()).contains("\"id\":\"module-01\"", "\"id\":\"lesson-0101\"");
    for (ParsedLesson lesson : lessons(track)) {
      assertThat(track.mindMapRoot()).contains("\"lesson_id\":\"%s\"".formatted(lesson.id()));
    }
    // One root, three modules, nine lessons and the concepts the lessons declare.
    assertThat(track.mindMapRoot().split("\"lesson_id\"", -1)).hasSize(28);
  }

  @Test
  void anEmptyCorpusLoadsNothing() {
    assertThat(CorpusParser.parse(new TreeMap<>()).tracks()).isEmpty();
  }

  // ---------------------------------------------------------------------------------------------
  // Refusals. Each one changes a single value in the corpus above.
  // ---------------------------------------------------------------------------------------------

  /**
   * Markup a content body may not carry. This is the refusal the whole mechanism exists for: seed
   * content written as SQL could carry markup the application itself would reject and nothing would
   * notice, because an INSERT statement crosses no write boundary. Here the body is examined by the
   * same predicate the administration API calls.
   */
  @Test
  void refusesABodyCarryingMarkupTheMarkdownValidatorRejects() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withAppendedTo(A_BODY, "\n<script>alert(1)</script>\n");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("Lesson body refused")
        .hasMessageContaining("what-a-value-holds.md")
        .hasMessageContaining("<script>");
  }

  /** The same predicate catches a link a browser would follow somewhere a body may not point. */
  @Test
  void refusesABodyWithALinkSchemeAContentBodyMayNotAddress() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withAppendedTo(A_BODY, "\n[run it](javascript:alert(1))\n");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("Lesson body refused")
        .hasMessageContaining("javascript:");
  }

  /**
   * A listing whose extension the corpus format does not map. Silently skipping it is the failure
   * mode worth stopping: the file is written, reviewed and committed, and the lesson ships with the
   * code it discusses missing.
   */
  @Test
  void refusesAListingWhoseExtensionIsOutsideTheClosedLanguageSet() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withReplacement(
            A_METADATA, "what-a-value-holds-1.py", "what-a-value-holds-1.vue");
    corpus.put(
        "fixture-path/examples/what-a-value-holds-1.vue",
        CorpusFixture.bytes("<template><p>hello</p></template>\n"));

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining(".vue")
        .hasMessageContaining("does not map to a language");
  }

  /** The extension decides the language, so a metadata file that claims another one is wrong. */
  @Test
  void refusesAListingWhoseDeclaredLanguageContradictsItsExtension() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withReplacement(
            A_METADATA, "\"language\": \"python\"", "\"language\": \"go\"");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("declares language 'go'")
        .hasMessageContaining("maps to 'python'");
  }

  /**
   * A mind map that would come out malformed: a lesson referenced from a track it does not belong
   * to. The set of lessons a track owns is worked out from the identifiers its block reserves, not
   * from what the lesson files claim, so a lesson carrying another track's identifier produces a
   * node pointing outside the map's own track.
   */
  @Test
  void refusesAMindMapThatWouldReferenceALessonFromAnotherTrack() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withReplacement(
            "fixture-path/lessons/handling-failure.json",
            "019205a0-3000-7000-8000-000000320203",
            "019205a0-3000-7000-8000-000000010203");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("Mind map node")
        .hasMessageContaining("does not belong to this track");
  }

  /** A lesson file nothing lists is a lesson nobody will ever read. */
  @Test
  void refusesALessonFileThatNoTrackJsonLists() {
    TreeMap<String, byte[]> corpus = CorpusFixture.copy();
    corpus.put(
        "fixture-path/lessons/an-orphan-lesson.md", CorpusFixture.bytes("## Why this exists\n"));
    corpus.put("fixture-path/lessons/an-orphan-lesson.json", CorpusFixture.bytes("{}\n"));

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("No track.json lists these files")
        .hasMessageContaining("an-orphan-lesson.md");
  }

  /** And the mirror image: a lesson that is listed but has no body. */
  @Test
  void refusesAListedLessonWithNoFile() {
    TreeMap<String, byte[]> corpus = CorpusFixture.copy();
    corpus.remove("fixture-path/lessons/handling-failure.md");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("lists lesson 'handling-failure'")
        .hasMessageContaining("does not exist");
  }

  /** A listed lesson with a body but no metadata is the same failure from the other side. */
  @Test
  void refusesAListedLessonWithNoMetadata() {
    TreeMap<String, byte[]> corpus = CorpusFixture.copy();
    corpus.remove("fixture-path/lessons/handling-failure.json");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("lessons/handling-failure.json does not exist");
  }

  /**
   * An identifier outside the block allocated to its track. Blocks are what let a developer
   * machine, continuous integration and a deployment address the same row, and an identifier from
   * another track's block silently attaches a module to the wrong lineage.
   */
  @Test
  void refusesAnIdentifierOutsideTheTracksAllocatedBlock() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withReplacement(
            TRACK_JSON,
            "019205a0-2000-7000-8000-000000003202",
            "019205a0-2000-7000-8000-000000000102");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("outside the block allocated")
        .hasMessageContaining("019205a0-2000-7000-8000-000000000102");
  }

  /**
   * The Angular track's identifiers predate the block scheme and are loaded as they are.
   *
   * <p>Its modules and lessons carry plain sequential suffixes. They are not renumbered because
   * progress rows on every installed client are keyed by lesson identifier: a new number does not
   * move a reader's completion, it detaches it, on devices this server cannot reach.
   *
   * <p>The corpus built here reproduces the real situation exactly, including its sharpest edge. It
   * is a rewritten track of nine lessons where the identifiers are <strong>not</strong> a function
   * of position: the third value in the series is skipped, because the lesson that held it has no
   * successor in the new plan and giving it to an unrelated lesson would hand that lesson someone
   * else's completions. That is precisely why the exception checks the shape of an identifier and
   * leaves the assignment to the author.
   */
  @Test
  void theLegacyTrackLoadsWithTheIdentifiersItWasSeededWith() {
    ParsedCorpus corpus = CorpusParser.parse(legacyShapedCorpus());

    ParsedTrack track = corpus.tracks().getFirst();
    assertThat(track.slug()).isEqualTo("angular-path");
    assertThat(track.id()).isEqualTo(UUID.fromString("019205a0-1000-7000-8000-000000000001"));
    assertThat(track.modules().stream().map(module -> module.id().toString()))
        .containsExactly(
            "019205a0-2000-7000-8000-000000000001",
            "019205a0-2000-7000-8000-000000000002",
            "019205a0-2000-7000-8000-000000000003");
    assertThat(lessons(track).stream().map(lesson -> lesson.id().toString()))
        .containsExactly(
            "019205a0-3000-7000-8000-000000000001",
            "019205a0-3000-7000-8000-000000000002",
            "019205a0-3000-7000-8000-000000000004",
            "019205a0-3000-7000-8000-000000000005",
            "019205a0-3000-7000-8000-000000000006",
            "019205a0-3000-7000-8000-000000000007",
            "019205a0-3000-7000-8000-000000000008",
            "019205a0-3000-7000-8000-000000000009",
            "019205a0-3000-7000-8000-000000000010");
    // The mind map resolves its references against the same series, so it is built rather than
    // refused.
    assertThat(track.mindMapRoot())
        .contains("\"lesson_id\":\"019205a0-3000-7000-8000-000000000010\"");
  }

  /**
   * The exception is one track wide.
   *
   * <p>A second track that picked up legacy-shaped identifiers has no reader completions to protect
   * and no reason to sit outside the scheme, so it is refused like any other.
   */
  @Test
  void theLegacyExceptionDoesNotExtendToAnyOtherTrack() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withReplacement(
            TRACK_JSON,
            "019205a0-2000-7000-8000-000000003202",
            "019205a0-2000-7000-8000-000000000002");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("outside the block allocated");
  }

  /** Nor does it let the legacy track reach into another track's block. */
  @Test
  void theLegacyTrackStillMayNotUseAnotherTracksBlock() {
    TreeMap<String, byte[]> corpus = legacyShapedCorpus();
    corpus.put(
        "angular-path/lessons/handling-failure.json",
        CorpusFixture.bytes(
            new String(
                    corpus.get("angular-path/lessons/handling-failure.json"),
                    java.nio.charset.StandardCharsets.UTF_8)
                .replace(
                    "019205a0-3000-7000-8000-000000000007",
                    "019205a0-3000-7000-8000-000000320203")));

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("does not belong to this track");
  }

  /**
   * The fixture corpus rewritten as the Angular track: the legacy directory name and slug, the
   * legacy track and module identifiers, and lesson identifiers from the legacy series with the
   * third value skipped.
   */
  private static TreeMap<String, byte[]> legacyShapedCorpus() {
    String[][] lessonIdentifiers = {
      {"019205a0-3000-7000-8000-000000320101", "019205a0-3000-7000-8000-000000000001"},
      {"019205a0-3000-7000-8000-000000320102", "019205a0-3000-7000-8000-000000000002"},
      {"019205a0-3000-7000-8000-000000320103", "019205a0-3000-7000-8000-000000000004"},
      {"019205a0-3000-7000-8000-000000320201", "019205a0-3000-7000-8000-000000000005"},
      {"019205a0-3000-7000-8000-000000320202", "019205a0-3000-7000-8000-000000000006"},
      {"019205a0-3000-7000-8000-000000320203", "019205a0-3000-7000-8000-000000000007"},
      {"019205a0-3000-7000-8000-000000320301", "019205a0-3000-7000-8000-000000000008"},
      {"019205a0-3000-7000-8000-000000320302", "019205a0-3000-7000-8000-000000000009"},
      {"019205a0-3000-7000-8000-000000320303", "019205a0-3000-7000-8000-000000000010"},
    };
    String[][] otherIdentifiers = {
      {"019205a0-1000-7000-8000-000000000032", "019205a0-1000-7000-8000-000000000001"},
      {"019205a0-2000-7000-8000-000000003201", "019205a0-2000-7000-8000-000000000001"},
      {"019205a0-2000-7000-8000-000000003202", "019205a0-2000-7000-8000-000000000002"},
      {"019205a0-2000-7000-8000-000000003203", "019205a0-2000-7000-8000-000000000003"},
      {"\"fixture-path\"", "\"angular-path\""},
    };

    TreeMap<String, byte[]> corpus = new TreeMap<>();
    CorpusFixture.valid()
        .forEach(
            (path, content) -> {
              String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
              for (String[] substitution : lessonIdentifiers) {
                text = text.replace(substitution[0], substitution[1]);
              }
              for (String[] substitution : otherIdentifiers) {
                text = text.replace(substitution[0], substitution[1]);
              }
              corpus.put(path.replace("fixture-path/", "angular-path/"), CorpusFixture.bytes(text));
            });
    return corpus;
  }

  /** A track identifier from no block at all. */
  @Test
  void refusesATrackIdentifierThatIsNotAnAllocatedBlock() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withReplacement(
            TRACK_JSON,
            "019205a0-1000-7000-8000-000000000032",
            "019205a0-1000-7000-8000-000000000099");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("outside every allocated block");
  }

  /** A track that cannot fill three modules waits for a later corpus rather than shipping thin. */
  @Test
  void refusesATrackWithoutThreeModules() {
    TreeMap<String, byte[]> corpus = CorpusFixture.copy();
    String json = new String(corpus.get(TRACK_JSON), java.nio.charset.StandardCharsets.UTF_8);
    int lastModule = json.lastIndexOf("    {");
    corpus.put(TRACK_JSON, CorpusFixture.bytes(json.substring(0, lastModule - 2) + "\n  ]\n}\n"));

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("a track has exactly 3 modules");
  }

  /**
   * A module's declared order is its zero-based position in the array, and the stored order is the
   * 1-based one the column holds. The identifier block is derived from the position, so a file that
   * disagrees with itself would put a module under an identifier belonging to another.
   */
  @Test
  void refusesAModuleWhoseDeclaredOrderIsNotItsPosition() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withReplacement(TRACK_JSON, "\"order\": 1,", "\"order\": 2,");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("sits at position 2 in the array, so its order is 1");
  }

  @Test
  void moduleOrderIsStoredOneBasedWhateverTheFileCounts() {
    ParsedTrack track = CorpusParser.parse(CorpusFixture.valid()).tracks().getFirst();

    assertThat(track.modules().stream().map(ParsedModule::displayOrder)).containsExactly(1, 2, 3);
  }

  /** An author who already wrote the taught-version sentence out gets it once, not twice. */
  @Test
  void theTaughtVersionIsNotRepeatedWhenTheAuthorAlreadyWroteIt() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withReplacement(
            TRACK_JSON,
            "groups code into modules.",
            "groups code into modules. Teaches Python 3.13.");

    ParsedTrack track = CorpusParser.parse(corpus).tracks().getFirst();

    assertThat(track.description()).endsWith("Teaches Python 3.13.");
    assertThat(track.description().split("Teaches Python 3.13.", -1)).hasSize(2);
  }

  @Test
  void refusesAModuleWithoutThreeLessons() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withReplacement(
            TRACK_JSON,
            "[\"what-a-value-holds\", \"naming-and-scope\", \"converting-between-types\"]",
            "[\"what-a-value-holds\", \"naming-and-scope\"]");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("has 2 lessons; a module has exactly 3");
  }

  /** A body too short to teach anything, and one long enough to be two lessons. */
  @Test
  void refusesABodyOutsideTheWordLimits() {
    TreeMap<String, byte[]> corpus = CorpusFixture.copy();
    corpus.put(A_BODY, CorpusFixture.bytes("## Why this exists\n\nToo short to teach anything.\n"));

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("words excluding fenced code")
        .hasMessageContaining("the range is 450 to 1000");
  }

  /** Words inside a fence are code, not prose, so they do not count toward the minimum. */
  @Test
  void fencedCodeDoesNotCountTowardTheWordLimits() {
    String fence = "\n```text\n" + "word ".repeat(2000) + "\n```\n";

    assertThat(CorpusParser.countProseWords("one two three" + fence)).isEqualTo(3);
  }

  /** A source outside the documentation allow-list fails review, so it fails the load. */
  @Test
  void refusesASourceOutsideTheDocumentationAllowList() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withReplacement(
            A_METADATA,
            "https://docs.python.org/3/reference/datamodel.html",
            "https://someone-s-blog.example.com/python-objects");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("not on the documentation allow-list");
  }

  /** A caption is rendered by interpolation, so it is held to the plain-text predicate. */
  @Test
  void refusesACaptionThatIsNotPlainText() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withReplacement(
            A_METADATA, "Two names, one object", "Two names, <b>one</b> object");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("Listing caption refused");
  }

  /** A misspelled key would otherwise be dropped, and the lesson would ship without the value. */
  @Test
  void refusesAnUnknownKeyInAMetadataFile() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withReplacement(A_METADATA, "\"difficulty\"", "\"difficultly\"");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("could not be read");
  }

  /** The directory name is the slug, so that a file's path says which track it belongs to. */
  @Test
  void refusesATrackWhoseSlugIsNotItsDirectoryName() {
    TreeMap<String, byte[]> corpus =
        CorpusFixture.withReplacement(TRACK_JSON, "\"fixture-path\"", "\"another-path\"");

    assertThatThrownBy(() -> CorpusParser.parse(corpus))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("The directory name is the slug");
  }

  private static List<ParsedLesson> lessons(ParsedTrack track) {
    List<ParsedLesson> lessons = new ArrayList<>();
    for (ParsedModule module : track.modules()) {
      lessons.addAll(module.lessons());
    }
    return lessons;
  }

  private static List<ParsedCodeExample> listings(ParsedTrack track) {
    List<ParsedCodeExample> listings = new ArrayList<>();
    for (ParsedLesson lesson : lessons(track)) {
      listings.addAll(lesson.codeExamples());
    }
    return listings;
  }
}
