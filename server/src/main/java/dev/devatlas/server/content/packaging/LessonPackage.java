package dev.devatlas.server.content.packaging;

import dev.devatlas.server.domain.Difficulty;
import java.util.List;
import java.util.UUID;

/**
 * The canonical, hashable shape of a lesson's packaged content -- content sync protocol §4.3,
 * matched field for field: {@code entity_id}, {@code entity_type}, {@code content_version}, {@code
 * slug}, {@code title}, {@code body_markdown}, {@code difficulty}, {@code estimated_minutes},
 * {@code module_id}, {@code order}, {@code code_examples[]}, {@code translations[]}.
 *
 * <p>This is the governing artifact for §5.4.1 of the REST contract: every field here is a field
 * the content sync protocol ships and hashes, so a write that changes any of them (other than
 * {@code entityId} and {@code entityType}, which never change once assigned) must bump {@code
 * lessons.content_version} and recompute the digest over this shape. {@code
 * ContentVersionBumpCoverageIT} reflects over these record components and fails the build if one is
 * added here with no corresponding bump scenario registered.
 *
 * <p>{@code content_version} <strong>is</strong> hashed (content sync protocol §3.3, §3.5's
 * required "adding a translation" test): the digest describes one exact version of the package, and
 * {@code translations} is itself part of that package, so a translation write already changes the
 * hashed bytes on its own -- excluding the counter from the hash would only make the digest
 * describe something less than the full version it is supposed to identify.
 *
 * @param entityId the lesson's own identifier -- immutable once assigned, so it has no bump
 *     scenario; this is {@code entity_id} on the wire, the identifier the manifest and the {@code
 *     GET /content/lesson/{entityId}} path address this package by
 * @param entityType always {@code "LESSON"} -- immutable, no bump scenario
 * @param contentVersion the revision counter; not independently settable, so it has no bump
 *     scenario of its own, but its value is part of the hashed bytes
 * @param slug carried in the package; any change bumps the version (§5.4.1's explicit example)
 * @param title lesson title
 * @param bodyMarkdown sanitized, LF-normalized markdown body
 * @param difficulty difficulty level
 * @param estimatedMinutes optional estimated minutes
 * @param moduleId the owning module -- changes when a lesson moves between modules (§5.4.1: "order,
 *     module membership (move)")
 * @param order 1-based position within the module
 * @param codeExamples the lesson's code examples, in {@code order} then {@code caption} order (§5);
 *     any create/update/delete/reorder among them bumps this same lesson's version (§5.4.1)
 * @param translations the lesson's stored non-English translations, in {@code locale} order (§5);
 *     creating, updating or deleting one bumps this same lesson's version (§5.4.1)
 */
public record LessonPackage(
    UUID entityId,
    String entityType,
    int contentVersion,
    String slug,
    String title,
    String bodyMarkdown,
    Difficulty difficulty,
    Integer estimatedMinutes,
    UUID moduleId,
    int order,
    List<CodeExamplePackageItem> codeExamples,
    List<TranslationPackageItem> translations) {

  public static final String ENTITY_TYPE = "LESSON";
}
