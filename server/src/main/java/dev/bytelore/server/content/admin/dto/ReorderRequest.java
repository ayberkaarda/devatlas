package dev.bytelore.server.content.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

/**
 * Shared body for every bulk-reorder endpoint (§5.4.2 modules, §5.4.3 lessons, §5.4.4 code
 * examples): every child of the parent, exactly once, in the desired order. A partial or
 * duplicate-laden list is rejected wholesale with {@code 409 ORDER_SET_INCOMPLETE}.
 */
public record ReorderRequest(@NotEmpty List<UUID> orderedIds) {}
