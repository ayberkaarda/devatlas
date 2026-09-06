package dev.bytelore.server.content.packaging;

import java.util.UUID;

/**
 * The canonical, hashable shape of a mind map's packaged content -- content sync protocol §4.3:
 * {@code content_version}, {@code entity_id}, {@code entity_type}, {@code root}, {@code track_id}.
 *
 * <p>Governs the same rule as {@link LessonPackage} for mind maps: {@code root} is the only field
 * here that a write can change (§5.4.1: "Mind map root tree create/update/delete"), and any change
 * to it bumps {@code mind_maps.content_version} and recomputes the digest. {@code entityId} and
 * {@code trackId} are identity, fixed at creation -- there is no endpoint that moves a mind map to
 * another track -- so they carry no bump scenario of their own; neither does {@code entityType},
 * which is always {@code "MIND_MAP"}. {@code contentVersion} is hashed like every other field,
 * consistent with {@link LessonPackage}.
 *
 * @param entityId the mind map's own identifier, distinct from {@code trackId}
 * @param entityType always {@code "MIND_MAP"}
 * @param contentVersion the revision counter, part of the hashed bytes
 * @param root the node tree, already validated JSON text, parsed and re-embedded canonically so the
 *     digest is computed over sorted keys throughout, not just at the top level
 * @param trackId the owning track
 */
public record MindMapPackage(
    UUID entityId, String entityType, int contentVersion, String root, UUID trackId) {

  public static final String ENTITY_TYPE = "MIND_MAP";
}
