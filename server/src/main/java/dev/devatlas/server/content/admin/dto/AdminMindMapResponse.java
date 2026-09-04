package dev.devatlas.server.content.admin.dto;

import dev.devatlas.server.content.dto.MindMapNodeResponse;
import java.time.Instant;
import java.util.UUID;

public record AdminMindMapResponse(
    UUID id,
    UUID trackId,
    int contentVersion,
    Instant updatedAt,
    MindMapNodeResponse root,
    long version) {}
