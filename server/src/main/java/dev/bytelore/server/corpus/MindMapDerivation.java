package dev.bytelore.server.corpus;

import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.MarkdownSanitizer;
import dev.bytelore.server.content.dto.MindMapNodeResponse;
import dev.bytelore.server.corpus.ParsedCorpus.ParsedLesson;
import dev.bytelore.server.corpus.ParsedCorpus.ParsedModule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a track's mind map out of the track's own structure, and then checks the result as if
 * somebody else had drawn it.
 *
 * <p>The map is derived rather than authored: the root is the track, its children are the modules,
 * each module's children are its lessons, and a lesson's declared concepts hang beneath it. A
 * hand-drawn map would be a second thing to keep in step with the lessons and a second thing to
 * review, and it would drift; a derived one cannot, because there is nothing for it to drift from.
 * The price is that the map says exactly what the structure says, which for a nine-lesson track is
 * the honest amount.
 *
 * <p><strong>Why derived output is still validated.</strong> The rule that does real work here is
 * lesson ownership, and it does real work only because the set of lessons a track owns is worked
 * out independently of what the lesson files declare: it is the set of identifiers the track's
 * allocated block reserves for its nine positions. A lesson file whose declared identifier belongs
 * to another track's block therefore produces a node pointing outside this track, and the map is
 * refused. Checking the declared identifiers against each other instead would be circular -- both
 * sides of the comparison would come from the same wrong file -- and would refuse nothing.
 *
 * <p>The depth, node-count and repeated-node ceilings cannot be reached from a well-formed track,
 * because the shape is fixed at four levels and node identifiers are positional. They are asserted
 * anyway: they are the ceilings the mind map write endpoint enforces, this derivation is code that
 * will be changed by somebody who has not read it, and an assertion that costs a few microseconds
 * is a cheaper way to find that out than a client refusing to render a map.
 */
final class MindMapDerivation {

  /** Matches the depth and node ceilings the mind map write endpoint enforces. */
  private static final int MAX_DEPTH = 8;

  private static final int MAX_NODES = 500;

  /** A node label is a short interpolated string; the write endpoint caps it at this length. */
  private static final int MAX_LABEL_LENGTH = 120;

  private MindMapDerivation() {}

  /**
   * Derives and validates the mind map for one track.
   *
   * @param trackTitle the track title, which becomes the root label
   * @param modules the track's modules, already parsed and in order
   * @param conceptsByLesson each lesson's declared concepts, which become its leaves
   * @param ownedLessonIds the identifiers this track's allocated block reserves for its lessons,
   *     computed from the block scheme rather than read from the lesson files
   * @return the root node, ready to be serialized into the {@code root} column
   * @throws CorpusException if the derived map repeats a node, exceeds a ceiling, carries a label
   *     that is not plain text, or references a lesson this track does not own
   */
  static MindMapNodeResponse derive(
      String trackTitle,
      List<ParsedModule> modules,
      Map<UUID, List<String>> conceptsByLesson,
      Set<UUID> ownedLessonIds) {
    List<MindMapNodeResponse> moduleNodes = new ArrayList<>();
    for (ParsedModule module : modules) {
      List<MindMapNodeResponse> lessonNodes = new ArrayList<>();
      for (ParsedLesson lesson : module.lessons()) {
        List<MindMapNodeResponse> conceptNodes = new ArrayList<>();
        List<String> concepts = conceptsByLesson.getOrDefault(lesson.id(), List.of());
        for (int i = 0; i < concepts.size(); i++) {
          conceptNodes.add(
              new MindMapNodeResponse(
                  "concept-%02d%02d-%d"
                      .formatted(module.displayOrder(), lesson.displayOrder(), i + 1),
                  concepts.get(i),
                  null,
                  List.of()));
        }
        lessonNodes.add(
            new MindMapNodeResponse(
                "lesson-%02d%02d".formatted(module.displayOrder(), lesson.displayOrder()),
                lesson.title(),
                lesson.id(),
                conceptNodes));
      }
      moduleNodes.add(
          new MindMapNodeResponse(
              "module-%02d".formatted(module.displayOrder()), module.title(), null, lessonNodes));
    }

    MindMapNodeResponse root = new MindMapNodeResponse("track", trackTitle, null, moduleNodes);
    validate(root, 1, new int[] {0}, new HashSet<>(), ownedLessonIds);
    return root;
  }

  private static void validate(
      MindMapNodeResponse node,
      int depth,
      int[] nodeCount,
      Set<String> seenNodeIds,
      Set<UUID> ownedLessons) {
    if (depth > MAX_DEPTH) {
      throw new CorpusException(
          "The derived mind map exceeds the maximum depth of %d.".formatted(MAX_DEPTH));
    }
    nodeCount[0]++;
    if (nodeCount[0] > MAX_NODES) {
      throw new CorpusException(
          "The derived mind map exceeds the maximum of %d nodes.".formatted(MAX_NODES));
    }
    if (!seenNodeIds.add(node.id())) {
      throw new CorpusException("The derived mind map repeats node '%s'.".formatted(node.id()));
    }
    if (node.lessonId() != null && !ownedLessons.contains(node.lessonId())) {
      throw new CorpusException(
          "Mind map node '%s' references lesson %s, which does not belong to this track."
              .formatted(node.id(), node.lessonId()));
    }
    if (node.label() == null || node.label().isBlank()) {
      throw new CorpusException("Mind map node '%s' has a blank label.".formatted(node.id()));
    }
    if (node.label().length() > MAX_LABEL_LENGTH) {
      throw new CorpusException(
          "Mind map node '%s' has a label of %d characters; the maximum is %d."
              .formatted(node.id(), node.label().length(), MAX_LABEL_LENGTH));
    }
    try {
      MarkdownSanitizer.validatePlainText(node.label(), "label of node '%s'".formatted(node.id()));
    } catch (ApiException e) {
      throw new CorpusException("Mind map label refused: " + e.getMessage(), e);
    }
    for (MindMapNodeResponse child : node.children()) {
      validate(child, depth + 1, nodeCount, seenNodeIds, ownedLessons);
    }
  }
}
