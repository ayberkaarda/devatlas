package dev.bytelore.server.content.packaging;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Produces the canonical package bytes for a lesson or a mind map, and the SHA-256 digest over
 * them, in one place -- the write path and any later re-derivation of the same package must go
 * through this class, never a hand-rolled second implementation (§5.4.1 of the REST contract: "the
 * computation lives in the service layer... never by a database trigger or generated column").
 *
 * <p>The wire shape and every byte-level rule below come from the content sync protocol (§3.2,
 * §4.3, §5), not from this class's own judgment: field names are the protocol's snake_case names
 * (an {@code entityId} record component becomes the {@code "entity_id"} map key), {@code
 * code_examples} is sorted by {@code order} then {@code caption}, {@code translations} is sorted by
 * {@code locale}, and the whole tree -- including the mind map's nested {@code root} -- is handed
 * to {@link CanonicalJson}, which sorts every object's keys recursively and drops {@code null}
 * values. No field here is invented independently of that document.
 */
@Service
public class ContentPackager {

  // A private, dependency-free mapper used only to parse a mind map's stored root text back into
  // a generic tree before it is canonicalized as part of the outer package -- never for anything
  // that leaves this class.
  private static final JsonMapper PARSER = JsonMapper.builder().build();

  public PackagedContent packageLesson(LessonPackage lessonPackage) {
    Map<String, Object> canonical = new LinkedHashMap<>();
    canonical.put("entity_id", lessonPackage.entityId().toString());
    canonical.put("entity_type", lessonPackage.entityType());
    canonical.put("content_version", lessonPackage.contentVersion());
    canonical.put("slug", lessonPackage.slug());
    canonical.put("title", lessonPackage.title());
    canonical.put("body_markdown", lessonPackage.bodyMarkdown());
    canonical.put(
        "difficulty",
        lessonPackage.difficulty() == null ? null : lessonPackage.difficulty().name());
    canonical.put("estimated_minutes", lessonPackage.estimatedMinutes());
    canonical.put("module_id", lessonPackage.moduleId().toString());
    canonical.put("order", lessonPackage.order());
    canonical.put("code_examples", codeExamples(lessonPackage.codeExamples()));
    canonical.put("translations", translations(lessonPackage.translations()));
    return pack(canonical);
  }

  public PackagedContent packageMindMap(MindMapPackage mindMapPackage) {
    Map<String, Object> canonical = new LinkedHashMap<>();
    canonical.put("entity_id", mindMapPackage.entityId().toString());
    canonical.put("entity_type", mindMapPackage.entityType());
    canonical.put("content_version", mindMapPackage.contentVersion());
    canonical.put("root", PARSER.readValue(mindMapPackage.root(), Object.class));
    canonical.put("track_id", mindMapPackage.trackId().toString());
    return pack(canonical);
  }

  /** §5: {@code code_examples} sorted by {@code order}, then {@code caption}. */
  private static List<Map<String, Object>> codeExamples(List<CodeExamplePackageItem> items) {
    return items.stream()
        .sorted(
            Comparator.comparingInt(CodeExamplePackageItem::order)
                .thenComparing(item -> item.caption() == null ? "" : item.caption()))
        .map(
            item -> {
              Map<String, Object> map = new LinkedHashMap<>();
              map.put("language", item.language());
              map.put("code", item.code());
              map.put("caption", item.caption());
              map.put("order", item.order());
              return map;
            })
        .toList();
  }

  /**
   * §5: {@code translations} sorted by {@code locale}.
   *
   * <p>The translated body is {@code body}, not {@code body_markdown}. The two names are not
   * interchangeable and the distinction is deliberate: a lesson's own body is {@code body_markdown}
   * at the top level of the package, while a translation entry mirrors the {@code (title, body)}
   * shape a translation row actually has -- the same name it carries in the read API and in a track
   * manifest's translations. A single spelling across all three is what lets a client parse a
   * translation the same way wherever it meets one.
   */
  private static List<Map<String, Object>> translations(List<TranslationPackageItem> items) {
    return items.stream()
        .sorted(Comparator.comparing(TranslationPackageItem::locale))
        .map(
            item -> {
              Map<String, Object> map = new LinkedHashMap<>();
              map.put("locale", item.locale());
              map.put("title", item.title());
              map.put("body", item.body());
              return map;
            })
        .toList();
  }

  private static PackagedContent pack(Map<String, Object> canonical) {
    byte[] bytes = CanonicalJson.bytes(canonical);
    return new PackagedContent(bytes, Sha256.hex(bytes), bytes.length);
  }
}
