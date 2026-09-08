package dev.bytelore.server.corpus;

import java.util.List;
import java.util.UUID;

/**
 * A corpus that has been read, normalized and validated, and holds nothing a database would refuse.
 *
 * <p>Everything in here is already in its stored form: bodies and captions normalized, listings
 * normalized as code, the mind map derived and serialized. {@link CorpusWriter} does no validation
 * of its own and no transformation -- it writes what this holds. Keeping the two apart is what lets
 * every refusal be tested without a database, and it means a change to the write path cannot
 * accidentally weaken a check.
 *
 * @param tracks the tracks of this corpus, ordered by slug
 */
public record ParsedCorpus(List<ParsedTrack> tracks) {

  /**
   * One track and everything beneath it.
   *
   * @param id the track's fixed identifier
   * @param slug the URL slug and directory name
   * @param title the track title
   * @param description the stored description, with the pinned version this track teaches as its
   *     trailing sentence
   * @param icon an optional icon name
   * @param displayOrder the track's position in the public list
   * @param modules exactly three modules, in order
   * @param mindMapId the derived identifier of this track's mind map
   * @param mindMapRoot the derived mind map, serialized as the JSON stored in the {@code root}
   *     column
   */
  public record ParsedTrack(
      UUID id,
      String slug,
      String title,
      String description,
      String icon,
      int displayOrder,
      List<ParsedModule> modules,
      UUID mindMapId,
      String mindMapRoot) {}

  /**
   * One module.
   *
   * @param id the module's fixed identifier
   * @param title the module title
   * @param displayOrder position within the track
   * @param estimatedMinutes optional reading estimate
   * @param lessons exactly three lessons, in order
   */
  public record ParsedModule(
      UUID id,
      String title,
      int displayOrder,
      Integer estimatedMinutes,
      List<ParsedLesson> lessons) {}

  /**
   * One lesson.
   *
   * @param id the lesson's fixed identifier
   * @param slug the globally unique lesson slug
   * @param title the lesson title
   * @param bodyMarkdown the normalized, validated markdown body
   * @param difficulty {@code BEGINNER}, {@code INTERMEDIATE} or {@code ADVANCED}
   * @param estimatedMinutes optional reading estimate
   * @param displayOrder position within the module
   * @param codeExamples the lesson's full listings, in order
   */
  public record ParsedLesson(
      UUID id,
      String slug,
      String title,
      String bodyMarkdown,
      String difficulty,
      Integer estimatedMinutes,
      int displayOrder,
      List<ParsedCodeExample> codeExamples) {}

  /**
   * One full listing.
   *
   * @param id the listing's derived identifier
   * @param language the language identifier the file's extension maps to
   * @param code the listing's source, normalized as code rather than as prose
   * @param caption an optional plain-text caption
   * @param displayOrder position within the lesson
   */
  public record ParsedCodeExample(
      UUID id, String language, String code, String caption, int displayOrder) {}
}
