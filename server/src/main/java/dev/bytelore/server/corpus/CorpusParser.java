package dev.bytelore.server.corpus;

import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.MarkdownSanitizer;
import dev.bytelore.server.common.TextNormalizer;
import dev.bytelore.server.content.dto.MindMapNodeResponse;
import dev.bytelore.server.corpus.CorpusDocuments.CodeExampleDocument;
import dev.bytelore.server.corpus.CorpusDocuments.LessonDocument;
import dev.bytelore.server.corpus.CorpusDocuments.ModuleDocument;
import dev.bytelore.server.corpus.CorpusDocuments.TrackDocument;
import dev.bytelore.server.corpus.ParsedCorpus.ParsedCodeExample;
import dev.bytelore.server.corpus.ParsedCorpus.ParsedLesson;
import dev.bytelore.server.corpus.ParsedCorpus.ParsedModule;
import dev.bytelore.server.corpus.ParsedCorpus.ParsedTrack;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a corpus directory into rows that are ready to be written, or refuses it.
 *
 * <p>This is where the write boundary moves rather than being bypassed. Content arriving through
 * the administration API is normalized and then examined by a set of static predicates -- the
 * markdown validator for a body, the plain-text validator for anything rendered by interpolation,
 * the closed language set for a listing. Those predicates need no HTTP request and no Spring
 * context, so the loader calls exactly the same ones. A body the API would refuse fails here, the
 * migration rolls back, startup fails and the build fails. Seed content written as SQL crossed no
 * such boundary at all, which is the hazard this whole mechanism exists to close.
 *
 * <p>Nothing here touches a database and nothing here computes a digest, which is what makes every
 * refusal testable in milliseconds against a corpus held in a map.
 *
 * <p><strong>Order of the passes.</strong> Files are grouped by track, each track is parsed and its
 * mind map derived, and only then does a whole-corpus audit look at identifiers and uniqueness
 * across tracks. The mind map is derived before that audit deliberately: it owns the rule that a
 * lesson must belong to the track that references it, and an earlier identifier sweep would refuse
 * the same file first and leave the map's rule as something no input can reach.
 */
public final class CorpusParser {

  /**
   * Deserialization is strict about unknown keys, and property names are snake_case to match the
   * files. A mapper of its own rather than the application's shared one: this runs inside a Flyway
   * migration, before the service layer exists, and a loader whose behaviour depends on a global
   * Jackson setting is a loader that changes when somebody tunes the API.
   */
  private static final JsonMapper JSON =
      JsonMapper.builder()
          .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
          .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .build();

  /** The fixed prefix every corpus identifier shares. */
  private static final String IDENTIFIER_PREFIX = "019205a0";

  /**
   * Highest track number the allocation reserves. Thirty-two blocks, so a new track never forces
   * one that already shipped to be renumbered.
   */
  private static final int MAX_TRACK_NUMBER = 32;

  private static final int MODULES_PER_TRACK = 3;
  private static final int LESSONS_PER_MODULE = 3;

  /**
   * The one track whose module and lesson identifiers predate the block scheme. See {@link
   * #isLegacyIdentifier} for why they are not renumbered.
   */
  private static final int LEGACY_IDENTIFIER_TRACK = 1;

  /** Highest sequence number the legacy series may carry. */
  private static final int MAX_LEGACY_SEQUENCE = 99;

  /** The identifier scheme allots two digits to a listing's position, so four is the ceiling. */
  private static final int MAX_LISTINGS_PER_LESSON = 4;

  private static final int MIN_BODY_WORDS = 450;
  private static final int MAX_BODY_WORDS = 1000;

  private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
  private static final Pattern ICON = Pattern.compile("^[a-z0-9-]+$");
  private static final Set<String> DIFFICULTIES = Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");

  /**
   * The hosts a lesson's sources may name. Official, versioned documentation only: a claim about
   * behaviour that is anchored to a blog post cannot be re-checked when the behaviour changes.
   * Adding a host is a decision recorded in the corpus format document, not something an author
   * does while writing.
   */
  private static final Set<String> SOURCE_HOSTS =
      Set.of(
          "angular.dev",
          "docs.spring.io",
          "docs.oracle.com",
          "typescriptlang.org",
          "developer.mozilla.org",
          "docs.python.org",
          "doc.rust-lang.org",
          "go.dev",
          "kotlinlang.org",
          "learn.microsoft.com",
          "www.php.net",
          "ruby-doc.org",
          "docs.djangoproject.com",
          "vuejs.org",
          "react.dev",
          "nodejs.org",
          "www.postgresql.org",
          "en.cppreference.com");

  private CorpusParser() {}

  /**
   * Reads, normalizes and validates a whole corpus.
   *
   * @param files relative path to raw bytes, as {@link CorpusFiles#read} produces
   * @return the parsed corpus, holding only values a database and the API would both accept
   * @throws CorpusException on the first thing that is wrong, naming the file it is wrong in
   */
  public static ParsedCorpus parse(SortedMap<String, byte[]> files) {
    if (files.isEmpty()) {
      return new ParsedCorpus(List.of());
    }

    Map<String, SortedMap<String, byte[]>> byTrackDirectory = new TreeMap<>();
    for (var entry : files.entrySet()) {
      int slash = entry.getKey().indexOf('/');
      if (slash < 0) {
        throw new CorpusException(
            "'%s' sits at the corpus root. A corpus holds one directory per track and nothing else."
                .formatted(entry.getKey()));
      }
      byTrackDirectory
          .computeIfAbsent(entry.getKey().substring(0, slash), key -> new TreeMap<>())
          .put(entry.getKey().substring(slash + 1), entry.getValue());
    }

    List<ParsedTrack> tracks = new ArrayList<>();
    for (var entry : byTrackDirectory.entrySet()) {
      tracks.add(parseTrack(entry.getKey(), entry.getValue()));
    }
    auditIdentifiers(tracks);
    return new ParsedCorpus(List.copyOf(tracks));
  }

  private static ParsedTrack parseTrack(String directory, SortedMap<String, byte[]> files) {
    Set<String> consumed = new HashSet<>();
    TrackDocument document =
        readJson(files, consumed, "track.json", directory, TrackDocument.class);

    String slug = requireText(document.slug(), "%s/track.json: 'slug'".formatted(directory), 80);
    if (!SLUG.matcher(slug).matches()) {
      throw new CorpusException(
          "%s/track.json: slug '%s' is not a valid slug.".formatted(directory, slug));
    }
    if (!slug.equals(directory)) {
      throw new CorpusException(
          "Track directory '%s' holds a track whose slug is '%s'. The directory name is the slug,"
                  .formatted(directory, slug)
              + " so that a file's path says which track it belongs to.");
    }

    int trackNumber = trackNumberOf(document.id(), directory);
    String title =
        requireText(document.title(), "%s/track.json: 'title'".formatted(directory), 200);
    String description =
        requireText(
            document.description(), "%s/track.json: 'description'".formatted(directory), 200);
    String teachesVersion =
        requireText(
            document.teachesVersion(),
            "%s/track.json: 'teaches_version'".formatted(directory),
            120);
    String icon = document.icon() == null ? null : TextNormalizer.normalize(document.icon());
    if (icon != null && (icon.length() > 64 || !ICON.matcher(icon).matches())) {
      throw new CorpusException(
          "%s/track.json: icon '%s' is not a valid icon name.".formatted(directory, icon));
    }
    int displayOrder =
        requireRange(document.order(), 1, 10000, "%s/track.json: 'order'".formatted(directory));

    // The pinned version a track teaches is stored as the description's trailing sentence rather
    // than in a column of its own. It is not decoration -- a track that does not say which version
    // it teaches makes claims nothing can check them against -- but it is also not something any
    // query filters on, and a new column would mean a schema migration for a sentence.
    //
    // Appending is idempotent: an author who already wrote the sentence out gets it once, not
    // twice. Both spellings occur in practice and neither is wrong, so the loader accommodates
    // both rather than making one of them a refusal over a full stop.
    String taughtVersionSentence = "Teaches %s.".formatted(teachesVersion);
    String storedDescription =
        description.endsWith(taughtVersionSentence)
            ? description
            : "%s %s".formatted(description, taughtVersionSentence);
    if (storedDescription.length() > 2000) {
      throw new CorpusException(
          "%s/track.json: description with the taught version appended is %d characters; the"
                  .formatted(directory, storedDescription.length())
              + " column holds 2000.");
    }

    List<ModuleDocument> moduleDocuments =
        document.modules() == null ? List.of() : document.modules();
    if (moduleDocuments.size() != MODULES_PER_TRACK) {
      throw new CorpusException(
          "%s/track.json: a track has exactly %d modules; this one has %d. A track that cannot fill"
                  .formatted(directory, MODULES_PER_TRACK, moduleDocuments.size())
              + " them waits for a later corpus rather than shipping thin.");
    }

    List<ParsedModule> modules = new ArrayList<>();
    Map<UUID, List<String>> conceptsByLesson = new LinkedHashMap<>();
    for (int m = 0; m < moduleDocuments.size(); m++) {
      modules.add(
          parseModule(
              directory,
              files,
              consumed,
              moduleDocuments.get(m),
              m + 1,
              trackNumber,
              conceptsByLesson));
    }

    MindMapNodeResponse root =
        MindMapDerivation.derive(title, modules, conceptsByLesson, allocatedLessonIds(trackNumber));

    requireEveryFileAccountedFor(directory, files, consumed);

    return new ParsedTrack(
        document.id(),
        slug,
        title,
        storedDescription,
        icon,
        displayOrder,
        List.copyOf(modules),
        uuid("4000", "0000000000%02d".formatted(trackNumber)),
        JSON.writeValueAsString(root));
  }

  private static ParsedModule parseModule(
      String directory,
      SortedMap<String, byte[]> files,
      Set<String> consumed,
      ModuleDocument document,
      int moduleNumber,
      int trackNumber,
      Map<UUID, List<String>> conceptsByLesson) {
    String where = "%s/track.json: module %d".formatted(directory, moduleNumber);
    if (document.id() == null) {
      throw new CorpusException("%s has no 'id'.".formatted(where));
    }
    String title = requireText(document.title(), "%s: 'title'".formatted(where), 200);
    // The corpus format numbers a module's `order` from zero, while `modules.display_order` is a
    // 1-based column with a CHECK that refuses 0. The two are not in conflict and neither is
    // adjustable: the file says where the module sits in the array, the column says where it sits
    // on a page, and the conversion is this one line. The declared value has to agree with the
    // module's actual position, because the identifier block is derived from that position and a
    // file that disagreed with itself would put a module under an identifier belonging to another.
    Integer declaredOrder = document.order();
    if (declaredOrder == null || declaredOrder != moduleNumber - 1) {
      throw new CorpusException(
          "%s declares order %s but sits at position %d in the array, so its order is %d. A"
                  .formatted(where, declaredOrder, moduleNumber, moduleNumber - 1)
              + " module's identifier is derived from its position, so the two cannot disagree.");
    }
    Integer estimatedMinutes = document.estimatedMinutes();
    if (estimatedMinutes != null) {
      requireRange(estimatedMinutes, 1, 6000, "%s: 'estimated_minutes'".formatted(where));
    }

    List<String> lessonSlugs = document.lessons() == null ? List.of() : document.lessons();
    if (lessonSlugs.size() != LESSONS_PER_MODULE) {
      throw new CorpusException(
          "%s has %d lessons; a module has exactly %d."
              .formatted(where, lessonSlugs.size(), LESSONS_PER_MODULE));
    }

    List<ParsedLesson> lessons = new ArrayList<>();
    for (int l = 0; l < lessonSlugs.size(); l++) {
      lessons.add(
          parseLesson(
              directory,
              files,
              consumed,
              lessonSlugs.get(l),
              trackNumber,
              moduleNumber,
              l + 1,
              conceptsByLesson));
    }

    return new ParsedModule(
        document.id(), title, moduleNumber, estimatedMinutes, List.copyOf(lessons));
  }

  private static ParsedLesson parseLesson(
      String directory,
      SortedMap<String, byte[]> files,
      Set<String> consumed,
      String lessonSlug,
      int trackNumber,
      int moduleNumber,
      int lessonNumber,
      Map<UUID, List<String>> conceptsByLesson) {
    if (lessonSlug == null || !SLUG.matcher(lessonSlug).matches() || lessonSlug.length() > 80) {
      throw new CorpusException(
          "%s/track.json lists '%s', which is not a valid lesson slug."
              .formatted(directory, lessonSlug));
    }
    String bodyPath = "lessons/%s.md".formatted(lessonSlug);
    String metadataPath = "lessons/%s.json".formatted(lessonSlug);
    if (!files.containsKey(bodyPath)) {
      throw new CorpusException(
          "%s/track.json lists lesson '%s' but %s/%s does not exist."
              .formatted(directory, lessonSlug, directory, bodyPath));
    }

    LessonDocument document =
        readJson(files, consumed, metadataPath, directory, LessonDocument.class);
    consumed.add(bodyPath);

    String where = "%s/%s".formatted(directory, bodyPath);
    String title =
        requireText(document.title(), "%s/%s: 'title'".formatted(directory, metadataPath), 200);
    if (document.id() == null) {
      throw new CorpusException("%s/%s has no 'id'.".formatted(directory, metadataPath));
    }
    String difficulty = document.difficulty();
    if (difficulty == null || !DIFFICULTIES.contains(difficulty)) {
      throw new CorpusException(
          "%s/%s: difficulty '%s' is not one of %s."
              .formatted(directory, metadataPath, difficulty, DIFFICULTIES));
    }
    Integer estimatedMinutes = document.estimatedMinutes();
    if (estimatedMinutes != null) {
      requireRange(
          estimatedMinutes,
          1,
          600,
          "%s/%s: 'estimated_minutes'".formatted(directory, metadataPath));
    }

    for (String source : document.sources() == null ? List.<String>of() : document.sources()) {
      requireAllowedSource(source, "%s/%s".formatted(directory, metadataPath));
    }

    List<String> concepts = new ArrayList<>();
    for (String concept : document.concepts() == null ? List.<String>of() : document.concepts()) {
      String normalized = TextNormalizer.normalize(concept);
      if (normalized == null || normalized.isBlank()) {
        throw new CorpusException("%s/%s: a concept is blank.".formatted(directory, metadataPath));
      }
      concepts.add(normalized);
    }
    conceptsByLesson.put(document.id(), List.copyOf(concepts));

    String body = TextNormalizer.normalize(CorpusFiles.text(files.get(bodyPath)));
    if (body == null || body.isBlank()) {
      throw new CorpusException("%s is empty.".formatted(where));
    }
    try {
      MarkdownSanitizer.validateMarkdown(body, where);
    } catch (ApiException e) {
      throw new CorpusException("Lesson body refused: " + e.getMessage(), e);
    }
    int words = countProseWords(body);
    if (words < MIN_BODY_WORDS || words > MAX_BODY_WORDS) {
      throw new CorpusException(
          "%s is %d words excluding fenced code; the range is %d to %d. A lesson that cannot reach"
                  .formatted(where, words, MIN_BODY_WORDS, MAX_BODY_WORDS)
              + " the minimum without padding has nothing to teach.");
    }

    List<CodeExampleDocument> listings =
        document.codeExamples() == null ? List.of() : document.codeExamples();
    if (listings.size() > MAX_LISTINGS_PER_LESSON) {
      throw new CorpusException(
          "%s/%s has %d listings; the identifier scheme allots two digits to a listing's position,"
                  .formatted(directory, metadataPath, listings.size())
              + " so %d is the ceiling.".formatted(MAX_LISTINGS_PER_LESSON));
    }
    List<ParsedCodeExample> codeExamples = new ArrayList<>();
    for (int e = 0; e < listings.size(); e++) {
      codeExamples.add(
          parseCodeExample(
              directory,
              files,
              consumed,
              listings.get(e),
              trackNumber,
              moduleNumber,
              lessonNumber,
              e + 1));
    }

    return new ParsedLesson(
        document.id(),
        lessonSlug,
        title,
        body,
        difficulty,
        estimatedMinutes,
        lessonNumber,
        List.copyOf(codeExamples));
  }

  private static ParsedCodeExample parseCodeExample(
      String directory,
      SortedMap<String, byte[]> files,
      Set<String> consumed,
      CodeExampleDocument document,
      int trackNumber,
      int moduleNumber,
      int lessonNumber,
      int listingNumber) {
    String where = "%s, listing %d".formatted(directory, listingNumber);
    String fileName = document.file();
    if (fileName == null || fileName.isBlank() || fileName.contains("/")) {
      throw new CorpusException(
          "%s names file '%s'. A listing names a plain file in the track's examples directory."
              .formatted(where, fileName));
    }
    String path = "examples/%s".formatted(fileName);
    if (!files.containsKey(path)) {
      throw new CorpusException(
          "%s names '%s/%s', which does not exist.".formatted(where, directory, path));
    }

    String language = CorpusLanguages.languageForExtension(fileName);
    if (document.language() != null && !language.equals(document.language())) {
      throw new CorpusException(
          "%s declares language '%s' but '%s' maps to '%s'. The extension decides, so the two"
                  .formatted(where, document.language(), fileName, language)
              + " cannot disagree.");
    }

    String code = TextNormalizer.normalizeCode(CorpusFiles.text(files.get(path)));
    if (code == null || code.isBlank()) {
      throw new CorpusException("%s/%s is empty.".formatted(directory, path));
    }
    if (code.length() > 20000) {
      throw new CorpusException(
          "%s/%s is %d characters; a listing holds 20000."
              .formatted(directory, path, code.length()));
    }

    String caption =
        document.caption() == null ? null : TextNormalizer.normalize(document.caption());
    if (caption != null) {
      if (caption.length() > 300) {
        throw new CorpusException(
            "%s has a caption of %d characters; the column holds 300."
                .formatted(where, caption.length()));
      }
      try {
        MarkdownSanitizer.validatePlainText(caption, "caption of %s".formatted(fileName));
      } catch (ApiException e) {
        throw new CorpusException("Listing caption refused: " + e.getMessage(), e);
      }
    }

    consumed.add(path);
    return new ParsedCodeExample(
        uuid(
            "5000",
            "0000%02d%02d%02d%02d"
                .formatted(trackNumber, moduleNumber, lessonNumber, listingNumber)),
        language,
        code,
        caption,
        listingNumber);
  }

  /**
   * Refuses any file the corpus holds that no {@code track.json} accounted for.
   *
   * <p>An unlisted lesson is the interesting case: the file is written, reviewed and committed, and
   * then never appears in the product because nothing references it. Expected-output files are the
   * one exception, since they are evidence for the verification runner rather than product and are
   * deliberately never stored.
   */
  private static void requireEveryFileAccountedFor(
      String directory, SortedMap<String, byte[]> files, Set<String> consumed) {
    Set<String> stray = new LinkedHashSet<>();
    for (String path : files.keySet()) {
      if (consumed.contains(path)) {
        continue;
      }
      if (path.startsWith("examples/") && CorpusLanguages.isExpectedOutput(path)) {
        continue;
      }
      stray.add("%s/%s".formatted(directory, path));
    }
    if (!stray.isEmpty()) {
      throw new CorpusException(
          "No track.json lists these files, so nothing would ever read them: %s.".formatted(stray));
    }
  }

  /**
   * The whole-corpus sweep: every identifier sits in the block its position allocates, and no two
   * rows claim the same identifier or the same slug.
   *
   * <p>Lesson identifiers are checked here <em>and</em> by the mind map derivation, which resolves
   * every lesson reference against the block its track reserves. That is deliberate rather than
   * accidental: the map's rule is the one that produces the message worth reading -- a node
   * pointing at another track's lesson is a broken map, not merely a wrong number -- and it runs
   * first, so it is the one anybody sees. This pass is what keeps the rule enforced if the
   * derivation is ever changed to stop looking.
   */
  private static void auditIdentifiers(List<ParsedTrack> tracks) {
    Map<UUID, String> identifiers = new HashMap<>();
    Map<String, String> slugs = new HashMap<>();

    for (ParsedTrack track : tracks) {
      int trackNumber = trackNumberOf(track.id(), track.slug());
      claimIdentifier(identifiers, track.id(), "track '%s'".formatted(track.slug()));
      claimSlug(slugs, track.slug(), "track '%s'".formatted(track.slug()));
      claimIdentifier(identifiers, track.mindMapId(), "mind map of '%s'".formatted(track.slug()));

      boolean legacyTrack = trackNumber == LEGACY_IDENTIFIER_TRACK;

      for (ParsedModule module : track.modules()) {
        UUID expected =
            uuid("2000", "00000000%02d%02d".formatted(trackNumber, module.displayOrder()));
        // The legacy track's modules are checked for shape only; every other track's are derived
        // from position. See isLegacyIdentifier for why one track is allowed to differ.
        boolean acceptable =
            legacyTrack ? isLegacyIdentifier(module.id(), "2000") : expected.equals(module.id());
        if (!acceptable) {
          throw new CorpusException(
              "Module %d of '%s' declares id %s, which is outside the block allocated to that"
                      .formatted(module.displayOrder(), track.slug(), module.id())
                  + " track; the block allocates %s.".formatted(expected));
        }
        claimIdentifier(
            identifiers,
            module.id(),
            "module %d of '%s'".formatted(module.displayOrder(), track.slug()));

        for (ParsedLesson lesson : module.lessons()) {
          UUID expectedLesson =
              uuid(
                  "3000",
                  "000000%02d%02d%02d"
                      .formatted(trackNumber, module.displayOrder(), lesson.displayOrder()));
          boolean acceptableLesson =
              legacyTrack
                  ? isLegacyIdentifier(lesson.id(), "3000")
                  : expectedLesson.equals(lesson.id());
          if (!acceptableLesson) {
            throw new CorpusException(
                "Lesson '%s' declares id %s, which is outside the block allocated to track '%s';"
                        .formatted(lesson.slug(), lesson.id(), track.slug())
                    + " the block allocates %s.".formatted(expectedLesson));
          }
          claimIdentifier(identifiers, lesson.id(), "lesson '%s'".formatted(lesson.slug()));
          claimSlug(slugs, lesson.slug(), "lesson '%s'".formatted(lesson.slug()));
          for (ParsedCodeExample example : lesson.codeExamples()) {
            claimIdentifier(
                identifiers,
                example.id(),
                "listing %d of '%s'".formatted(example.displayOrder(), lesson.slug()));
          }
        }
      }
    }
  }

  private static void claimIdentifier(Map<UUID, String> seen, UUID id, String owner) {
    String previous = seen.putIfAbsent(id, owner);
    if (previous != null) {
      throw new CorpusException(
          "Identifier %s is claimed by both %s and %s.".formatted(id, previous, owner));
    }
  }

  private static void claimSlug(Map<String, String> seen, String slug, String owner) {
    String previous = seen.putIfAbsent(slug, owner);
    if (previous != null) {
      throw new CorpusException(
          "Slug '%s' is claimed by both %s and %s.".formatted(slug, previous, owner));
    }
  }

  /**
   * The lesson identifiers a track may use, which is what the mind map resolves its lesson
   * references against.
   *
   * <p>For every track but one this is the nine values its block reserves, derived from the scheme
   * alone. Track {@value #LEGACY_IDENTIFIER_TRACK} is the exception described on {@link
   * #isLegacyIdentifier}: its lessons are the legacy series, and any of them will do.
   */
  private static Set<UUID> allocatedLessonIds(int trackNumber) {
    Set<UUID> allocated = new LinkedHashSet<>();
    if (trackNumber == LEGACY_IDENTIFIER_TRACK) {
      for (int sequence = 1; sequence <= MAX_LEGACY_SEQUENCE; sequence++) {
        allocated.add(uuid("3000", "0000000000%02d".formatted(sequence)));
      }
      return allocated;
    }
    for (int module = 1; module <= MODULES_PER_TRACK; module++) {
      for (int lesson = 1; lesson <= LESSONS_PER_MODULE; lesson++) {
        allocated.add(uuid("3000", "000000%02d%02d%02d".formatted(trackNumber, module, lesson)));
      }
    }
    return allocated;
  }

  /**
   * Whether an identifier belongs to the series that predates the block scheme: ten zeros and then
   * a plain sequence number.
   *
   * <p><strong>This is the Angular track's exception, and it is deliberately narrow.</strong> Track
   * {@value #LEGACY_IDENTIFIER_TRACK} was seeded before blocks existed and its modules and lessons
   * carry plain sequential suffixes -- {@code ...-000000000001}, {@code ...-000000000002} -- where
   * the scheme would want {@code ...-000000000101}. Its track and mind map identifiers happen to
   * match the scheme already; only these two levels do not.
   *
   * <p>They keep them, and this is why: <em>progress rows on every installed client are keyed by
   * lesson identifier.</em> Renumbering a lesson does not move a reader's completion to the new
   * number, it detaches it -- silently, with no error anywhere, on a device this server cannot
   * reach. Every reader who had finished that lesson would open the application and find it
   * unfinished. There is no migration that fixes it afterwards, because the rows that would have to
   * be rewritten are on the clients.
   *
   * <p>Which lesson keeps which identifier is therefore the author's decision rather than a
   * position in an array: when a track is rewritten, the author says which new lesson succeeds
   * which old one, and the identifier follows the succession. So for this one track the audit
   * checks the <em>shape</em> of an identifier and leaves the assignment alone, while global
   * uniqueness still stops two lessons claiming the same one. Every other track is held to exact
   * positional derivation, and a second track that picked up legacy-shaped identifiers is refused.
   *
   * <p>The two shapes cannot be confused: a block identifier always carries a nonzero track number
   * in the digits this series leaves zero.
   */
  private static boolean isLegacyIdentifier(UUID id, String group) {
    String text = id.toString();
    return text.startsWith("%s-%s-7000-8000-0000000000".formatted(IDENTIFIER_PREFIX, group))
        && !text.endsWith("00");
  }

  private static int trackNumberOf(UUID id, String where) {
    if (id == null) {
      throw new CorpusException("%s: track.json has no 'id'.".formatted(where));
    }
    String text = id.toString();
    for (int number = 1; number <= MAX_TRACK_NUMBER; number++) {
      if (uuid("1000", "0000000000%02d".formatted(number)).toString().equals(text)) {
        return number;
      }
    }
    throw new CorpusException(
        "%s: track id %s is outside every allocated block. A track identifier is"
                .formatted(where, id)
            + " %s-1000-7000-8000-0000000000TT with TT from 01 to %02d."
                .formatted(IDENTIFIER_PREFIX, MAX_TRACK_NUMBER));
  }

  private static UUID uuid(String group, String node) {
    return UUID.fromString("%s-%s-7000-8000-%s".formatted(IDENTIFIER_PREFIX, group, node));
  }

  private static void requireAllowedSource(String source, String where) {
    if (source == null || source.isBlank()) {
      throw new CorpusException("%s: a source is blank.".formatted(where));
    }
    URI uri;
    try {
      uri = new URI(source);
    } catch (URISyntaxException e) {
      throw new CorpusException("%s: source '%s' is not a URL.".formatted(where, source), e);
    }
    String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
    if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) {
      throw new CorpusException("%s: source '%s' is not an https URL.".formatted(where, source));
    }
    for (String allowed : SOURCE_HOSTS) {
      if (host.equals(allowed) || host.endsWith("." + allowed)) {
        return;
      }
    }
    throw new CorpusException(
        "%s: source '%s' is on host '%s', which is not on the documentation allow-list. Adding a"
                .formatted(where, source, host)
            + " host is a recorded decision, not something an author does while writing.");
  }

  /**
   * Counts the words a reader actually reads: everything outside a fenced code block.
   *
   * <p>Fences are found by scanning lines rather than by a regular expression over the whole
   * document, because a fence is a line-level construct and a pattern that spans lines gets the
   * nesting wrong in exactly the cases a long listing produces.
   */
  static int countProseWords(String body) {
    int words = 0;
    boolean insideFence = false;
    for (String line : body.split("\n", -1)) {
      String trimmed = line.strip();
      if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
        insideFence = !insideFence;
        continue;
      }
      if (insideFence) {
        continue;
      }
      for (String token : trimmed.split("\\s+")) {
        if (!token.isEmpty()) {
          words++;
        }
      }
    }
    return words;
  }

  private static <T> T readJson(
      SortedMap<String, byte[]> files,
      Set<String> consumed,
      String path,
      String directory,
      Class<T> type) {
    byte[] raw = files.get(path);
    if (raw == null) {
      throw new CorpusException("%s/%s does not exist.".formatted(directory, path));
    }
    consumed.add(path);
    try {
      return JSON.readValue(CorpusFiles.text(raw), type);
    } catch (JacksonException e) {
      throw new CorpusException(
          "%s/%s could not be read: %s".formatted(directory, path, e.getOriginalMessage()), e);
    }
  }

  private static String requireText(String value, String where, int maxLength) {
    String normalized = TextNormalizer.normalize(value);
    if (normalized == null || normalized.isBlank()) {
      throw new CorpusException("%s is missing or blank.".formatted(where));
    }
    if (normalized.length() > maxLength) {
      throw new CorpusException(
          "%s is %d characters; the maximum is %d."
              .formatted(where, normalized.length(), maxLength));
    }
    return normalized;
  }

  private static int requireRange(Integer value, int min, int max, String where) {
    if (value == null) {
      throw new CorpusException("%s is missing.".formatted(where));
    }
    if (value < min || value > max) {
      throw new CorpusException(
          "%s is %d; the range is %d to %d.".formatted(where, value, min, max));
    }
    return value;
  }
}
