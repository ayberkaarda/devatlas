package dev.devatlas.server.content;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The one place {@code order} is computed (§7 of the REST contract: "order normalization").
 *
 * <p>{@code order} is a 1-based, contiguous, unique sequence within a parent, and the server owns
 * it entirely. A submitted {@code order} means "insert at this position": a value beyond the
 * current child count appends, a value colliding with an existing child inserts before it and
 * pushes the rest down, and an omitted value appends. Every write that affects ordering -- create,
 * update, delete, move between parents, or a bulk reorder -- renumbers the parent's remaining
 * children to {@code 1..n} in the same transaction, so a client never sees a gap, a duplicate or a
 * zero.
 */
public final class OrderNormalizer {

  private OrderNormalizer() {}

  /**
   * Places {@code itemId} at {@code requestedPosition} (1-based, {@code null} meaning "append")
   * among the parent's other children and returns the full ordered id list. The caller then assigns
   * {@code order = index + 1} to each id in the result, in order.
   *
   * @param existingOrderedIds the parent's current children, in their current order, including
   *     {@code itemId} if it already exists among them
   * @param itemId the id being created, updated or moved
   * @param requestedPosition the caller's requested 1-based position, or {@code null} to append
   */
  public static List<UUID> placeAndRenumber(
      List<UUID> existingOrderedIds, UUID itemId, Integer requestedPosition) {
    List<UUID> withoutItem = new ArrayList<>(existingOrderedIds);
    withoutItem.remove(itemId);

    int insertIndex =
        requestedPosition == null
            ? withoutItem.size()
            : Math.min(Math.max(requestedPosition - 1, 0), withoutItem.size());
    withoutItem.add(insertIndex, itemId);
    return withoutItem;
  }

  /**
   * Removes {@code itemId} from the parent's children; the remainder is renumbered by the caller.
   */
  public static List<UUID> removeAndRenumber(List<UUID> existingOrderedIds, UUID itemId) {
    List<UUID> copy = new ArrayList<>(existingOrderedIds);
    copy.remove(itemId);
    return copy;
  }

  /**
   * Validates a bulk reorder payload: it must name every current child exactly once. A partial
   * list, a duplicate, or an id that does not belong to the parent is rejected wholesale with
   * {@code 409 ORDER_SET_INCOMPLETE} rather than applied partially -- this is what makes reordering
   * a single atomic call instead of N racing single-item moves.
   */
  public static void requireCompleteSet(List<UUID> currentIds, List<UUID> requestedOrderedIds) {
    Set<UUID> current = new HashSet<>(currentIds);
    Set<UUID> requested = new HashSet<>(requestedOrderedIds);
    if (requestedOrderedIds.size() != currentIds.size()
        || requested.size() != requestedOrderedIds.size()
        || !requested.equals(current)) {
      throw new ApiException(
          ErrorCode.ORDER_SET_INCOMPLETE,
          "The reorder payload must list every child of the parent exactly once.");
    }
  }
}
