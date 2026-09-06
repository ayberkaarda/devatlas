package dev.bytelore.server.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * {@link PageQuery#resolve} and, in particular, its {@code sort} parsing.
 *
 * <p>A controller method parameter typed {@code List<String> sort} never actually receives a list
 * element containing a comma when the query string carries exactly one {@code sort} occurrence: the
 * web framework's own conversion from a single delimited query value to a {@code List<String>}
 * splits on commas before this class ever sees the value, so {@code ?sort=created_at,desc} arrives
 * as two separate list elements, {@code "created_at"} and {@code "desc"}, not one. These cases
 * exercise that shape directly -- a caller passing separate elements, one still carrying an
 * embedded comma, and the boundary cases a direction-aware, positional read of the element stream
 * has to get right.
 */
class PageQueryTest {

  private static final Map<String, String> FIELDS =
      Map.of(
          "title", "title",
          "created_at", "createdAt",
          "updated_at", "updatedAt");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "createdAt");

  private static Sort resolveSort(List<String> sort) {
    Pageable pageable = PageQuery.resolve(null, null, sort, FIELDS, DEFAULT_SORT);
    return pageable.getSort();
  }

  @Test
  void aFieldWithNoDirectionDefaultsToAscending() {
    assertThat(resolveSort(List.of("title"))).isEqualTo(Sort.by(Sort.Direction.ASC, "title"));
  }

  /**
   * The shape a real request actually delivers: the framework has already split {@code
   * "created_at,desc"} into two list elements before {@link PageQuery} sees them.
   */
  @Test
  void aDirectionDeliveredAsItsOwnListElementAppliesToThePrecedingField() {
    assertThat(resolveSort(List.of("created_at", "desc")))
        .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  /** The same result when the comma survives as one element, so both shapes are supported. */
  @Test
  void aDirectionEmbeddedInASingleElementWithItsFieldAppliesTheSameWay() {
    assertThat(resolveSort(List.of("created_at,desc")))
        .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Test
  void directionMatchingIsCaseInsensitive() {
    assertThat(resolveSort(List.of("created_at", "DESC")))
        .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Test
  void multipleFieldsEachKeepTheirOwnDirection() {
    assertThat(resolveSort(List.of("title", "asc", "created_at", "desc")))
        .isEqualTo(
            Sort.by(
                new Sort.Order(Sort.Direction.ASC, "title"),
                new Sort.Order(Sort.Direction.DESC, "createdAt")));
  }

  @Test
  void aFieldFollowedByAnotherFieldWithNoDirectionInBetweenDefaultsTheFirstToAscending() {
    assertThat(resolveSort(List.of("title", "created_at")))
        .isEqualTo(
            Sort.by(
                new Sort.Order(Sort.Direction.ASC, "title"),
                new Sort.Order(Sort.Direction.ASC, "createdAt")));
  }

  @Test
  void aListStartingWithADirectionTokenIsRejected() {
    assertThatExceptionOfType(ApiException.class)
        .isThrownBy(() -> resolveSort(List.of("desc")))
        .extracting(ApiException::code)
        .isEqualTo(ErrorCode.INVALID_SORT_FIELD);
  }

  @Test
  void aSecondDirectionForTheSameFieldIsRejected() {
    assertThatExceptionOfType(ApiException.class)
        .isThrownBy(() -> resolveSort(List.of("created_at", "desc", "asc")))
        .extracting(ApiException::code)
        .isEqualTo(ErrorCode.INVALID_SORT_FIELD);
  }

  @Test
  void aFieldNotInTheWhitelistIsRejected() {
    assertThatExceptionOfType(ApiException.class)
        .isThrownBy(() -> resolveSort(List.of("bogus")))
        .extracting(ApiException::code)
        .isEqualTo(ErrorCode.INVALID_SORT_FIELD);
  }

  @Test
  void anEmptySortFallsBackToTheDefault() {
    assertThat(resolveSort(List.of())).isEqualTo(DEFAULT_SORT);
  }
}
