package dev.bytelore.server.corpus;

import java.util.List;
import java.util.UUID;

/**
 * The JSON files of a corpus, as they are written on disk.
 *
 * <p>These are the corpus format's own shapes, deliberately separate from both the API's request
 * records and the rows the loader writes. Reading straight into a request record would tie the file
 * format to whatever the administration API happens to accept this month; reading straight into a
 * row would leave no place for the fields the corpus carries but the schema does not, such as the
 * pinned version a track teaches and the sources a lesson is anchored to.
 *
 * <p>Deserialization fails on an unknown property. A misspelled key that is silently ignored is the
 * worst outcome available here: the corpus looks complete, the migration succeeds, and a lesson
 * arrives with its difficulty or its listings quietly missing.
 */
final class CorpusDocuments {

  private CorpusDocuments() {}

  /**
   * {@code track.json}.
   *
   * @param id the track's fixed identifier, from the block allocated to this track
   * @param slug the URL slug, which is also the name of the track's directory
   * @param title the track title
   * @param description one sentence describing the track
   * @param icon an optional icon name
   * @param order the track's position in the public list
   * @param teachesVersion the single pinned version this track teaches, required because an
   *     unversioned claim cannot be checked against anything
   * @param modules exactly three modules, in order
   */
  record TrackDocument(
      UUID id,
      String slug,
      String title,
      String description,
      String icon,
      Integer order,
      String teachesVersion,
      List<ModuleDocument> modules) {}

  /**
   * One entry of {@code track.json}'s {@code modules} array.
   *
   * @param id the module's fixed identifier
   * @param title the module title
   * @param order the module's position within the track, which must match its position in the array
   * @param estimatedMinutes optional reading estimate for the whole module
   * @param lessons exactly three lesson slugs, whose order in this array is their order in the
   *     module
   */
  record ModuleDocument(
      UUID id, String title, Integer order, Integer estimatedMinutes, List<String> lessons) {}

  /**
   * {@code lessons/<slug>.json}. The slug itself is the file name, not a field: two answers to
   * "what is this lesson's slug" is one answer too many.
   *
   * @param id the lesson's fixed identifier
   * @param title the lesson title, which the body must not repeat as a heading
   * @param difficulty one of {@code BEGINNER}, {@code INTERMEDIATE}, {@code ADVANCED}
   * @param estimatedMinutes optional reading estimate
   * @param sources the official documentation this lesson's claims are anchored to
   * @param concepts the labels this lesson contributes to the derived mind map
   * @param codeExamples the full listings, each naming a file in {@code examples/}
   */
  record LessonDocument(
      UUID id,
      String title,
      String difficulty,
      Integer estimatedMinutes,
      List<String> sources,
      List<String> concepts,
      List<CodeExampleDocument> codeExamples) {}

  /**
   * One entry of a lesson's {@code code_examples} array.
   *
   * @param language the language identifier the author expects, checked against the one the file's
   *     extension maps to rather than trusted
   * @param caption a short plain-text caption
   * @param file the listing's file name within the track's {@code examples/} directory
   */
  record CodeExampleDocument(String language, String caption, String file) {}
}
