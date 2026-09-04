package dev.devatlas.server.content.dto;

import java.util.List;
import java.util.UUID;

/**
 * One node of a mind map tree (§5.2.4). Also the shape stored, verbatim, as the {@code root} JSONB
 * column -- reading a stored map back is exactly {@code jsonMapper.readValue(root,
 * MindMapNodeResponse.class)}, so the storage format and the wire format never drift apart.
 *
 * @param id unique within the map, {@code ^[a-z0-9][a-z0-9-]{0,63}$}
 * @param label 1-120 chars, sanitized to plain text
 * @param lessonId optional; must reference a lesson in the same track
 * @param children never {@code null}, may be {@code []}
 */
public record MindMapNodeResponse(
    String id, String label, UUID lessonId, List<MindMapNodeResponse> children) {}
