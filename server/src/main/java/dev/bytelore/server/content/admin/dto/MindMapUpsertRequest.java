package dev.bytelore.server.content.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * {@code PUT /admin/tracks/{trackId}/mindmap} (§5.4.6): an upsert of the whole tree. {@code
 * version} is omitted on first creation and required on every later write.
 */
public record MindMapUpsertRequest(@NotNull @Valid MindMapNodeRequest root, Long version) {}
