package dev.bytelore.server.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * A pure unit test, no Spring context: {@link GlobalExceptionHandler}'s methods are ordinary
 * instance methods -- {@code @ExceptionHandler} is metadata Spring's dispatcher reads, not
 * something that prevents calling a method directly -- so the mapping from an exception type to the
 * {@code {code, message}} envelope can be pinned down this cheaply.
 *
 * <p>This is the regression guard for the fix in this phase: before it, {@link
 * DataIntegrityViolationException} fell through to {@link GlobalExceptionHandler#handleUnexpected},
 * which answers a bare {@code 500 INTERNAL_ERROR} with no information about what went wrong -- a
 * raw database fault escaping to a caller as an undifferentiated server error, for a class of
 * failure (a check constraint the service layer's own validation did not mirror) that is a fact
 * about the request, not about the server.
 */
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void aDataIntegrityViolationIsMappedToValidationFailedNotARaw500() {
    DataIntegrityViolationException exception =
        new DataIntegrityViolationException(
            "ERROR: new row for relation \"whitelist_sources\" violates check constraint"
                + " \"ck_whitelist_sources_feed_url\"");

    ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(exception);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_FAILED");
    // The driver's own message -- which can name a column, a constraint, or a literal value from
    // the request -- must never reach the response body.
    assertThat(response.getBody().message())
        .doesNotContain("ck_whitelist_sources_feed_url")
        .doesNotContain("whitelist_sources");
  }

  @Test
  void anUnmappedExceptionStillFallsBackToARaw500() {
    ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new RuntimeException("boom"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.getBody().message()).doesNotContain("boom");
  }
}
