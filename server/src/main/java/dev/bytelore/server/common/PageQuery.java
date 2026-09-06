package dev.bytelore.server.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Resolves the {@code page}, {@code size} and {@code sort} query parameters shared by every
 * collection endpoint (§2.5 of the REST contract) into a Spring Data {@link Pageable}.
 *
 * <p>Sorting is whitelisted per endpoint rather than passed through to JPA verbatim: {@code
 * allowedFields} maps the wire name a caller may request (e.g. {@code "updated_at"}) to the JPA
 * entity property it actually sorts on (e.g. {@code "updatedAt"}). A field outside that map is
 * rejected with {@link ErrorCode#INVALID_SORT_FIELD} rather than silently ignored or handed to the
 * query planner verbatim.
 *
 * <p>{@code sort} is documented as a series of {@code field,direction} pairs (e.g. {@code
 * updated_at,desc}), but a controller method parameter typed {@code List<String> sort} never
 * actually receives a list element containing a comma when the query string carries exactly one
 * {@code sort} occurrence: the web framework's own conversion from a single delimited query value
 * to a {@code List<String>} splits on commas before this class ever sees the value, so {@code
 * ?sort=created_at,desc} arrives here as two separate list elements, {@code "created_at"} and
 * {@code "desc"}, not one. Treating every list element as an independent {@code field[,direction]}
 * token -- the previous approach -- therefore rejected the direction as an unrecognised field on
 * every single-field sort. Sort parsing instead reads the (already comma-flattened) element stream
 * positionally: an {@code asc}/{@code desc} element is the direction of the field immediately
 * before it, and anything else starts a new field. A stray comma inside one element is still split
 * here too, so a caller or test that supplies an intact {@code "field,direction"} string keeps
 * working exactly the same way.
 */
public final class PageQuery {

  private static final int MAX_SIZE = 100;
  private static final int DEFAULT_SIZE = 20;

  private PageQuery() {}

  public static Pageable resolve(
      Integer page,
      Integer size,
      List<String> sort,
      Map<String, String> allowedFields,
      Sort defaultSort) {
    int resolvedPage = page == null ? 0 : page;
    if (resolvedPage < 0) {
      throw new ApiException(ErrorCode.INVALID_PARAMETER, "'page' must be >= 0.");
    }
    int resolvedSize = size == null ? DEFAULT_SIZE : size;
    if (resolvedSize < 1 || resolvedSize > MAX_SIZE) {
      throw new ApiException(
          ErrorCode.PAGE_SIZE_EXCEEDED, "'size' must be between 1 and %d.".formatted(MAX_SIZE));
    }

    Sort resolvedSort =
        (sort == null || sort.isEmpty()) ? defaultSort : parseSort(sort, allowedFields);
    return PageRequest.of(resolvedPage, resolvedSize, resolvedSort);
  }

  private static Sort parseSort(List<String> sort, Map<String, String> allowedFields) {
    List<String> tokens = flatten(sort);
    List<Sort.Order> orders = new ArrayList<>();
    // The property a trailing direction token, if one comes next, applies to. Finalised (added to
    // `orders` with an implicit ASC) as soon as either another field token or the end of the
    // stream is reached without a direction ever showing up for it.
    String pendingProperty = null;

    for (String token : tokens) {
      if (isDirectionToken(token)) {
        if (pendingProperty == null) {
          throw new ApiException(
              ErrorCode.INVALID_SORT_FIELD,
              "'%s' is a sort direction with no preceding field to apply it to.".formatted(token));
        }
        orders.add(new Sort.Order(directionOf(token), pendingProperty));
        pendingProperty = null;
        continue;
      }

      if (pendingProperty != null) {
        orders.add(new Sort.Order(Sort.Direction.ASC, pendingProperty));
      }
      pendingProperty = allowedFields.get(token);
      if (pendingProperty == null) {
        throw new ApiException(
            ErrorCode.INVALID_SORT_FIELD,
            "'%s' is not a sortable field on this endpoint.".formatted(token));
      }
    }
    if (pendingProperty != null) {
      orders.add(new Sort.Order(Sort.Direction.ASC, pendingProperty));
    }
    return Sort.by(orders);
  }

  private static List<String> flatten(List<String> sort) {
    List<String> tokens = new ArrayList<>();
    for (String entry : sort) {
      if (entry == null) {
        continue;
      }
      for (String piece : entry.split(",")) {
        String trimmed = piece.trim();
        if (!trimmed.isEmpty()) {
          tokens.add(trimmed);
        }
      }
    }
    return tokens;
  }

  private static boolean isDirectionToken(String token) {
    return "asc".equalsIgnoreCase(token) || "desc".equalsIgnoreCase(token);
  }

  private static Sort.Direction directionOf(String token) {
    return "desc".equalsIgnoreCase(token) ? Sort.Direction.DESC : Sort.Direction.ASC;
  }
}
