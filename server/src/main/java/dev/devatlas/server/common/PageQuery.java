package dev.devatlas.server.common;

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
    List<Sort.Order> orders = new ArrayList<>();
    for (String token : sort) {
      String[] parts = token.split(",", 2);
      String wireField = parts[0].trim();
      String property = allowedFields.get(wireField);
      if (property == null) {
        throw new ApiException(
            ErrorCode.INVALID_SORT_FIELD,
            "'%s' is not a sortable field on this endpoint.".formatted(wireField));
      }
      Sort.Direction direction = Sort.Direction.ASC;
      if (parts.length == 2) {
        String directionToken = parts[1].trim();
        if ("desc".equalsIgnoreCase(directionToken)) {
          direction = Sort.Direction.DESC;
        } else if (!"asc".equalsIgnoreCase(directionToken)) {
          throw new ApiException(
              ErrorCode.INVALID_SORT_FIELD,
              "'%s' has an invalid sort direction; use 'asc' or 'desc'.".formatted(token));
        }
      }
      orders.add(new Sort.Order(direction, property));
    }
    return Sort.by(orders);
  }
}
