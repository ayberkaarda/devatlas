package dev.devatlas.server.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The body of every non-2xx response.
 *
 * <p>{@code errors} is the single additive extension to the envelope and is present only on
 * validation failures; a client that reads nothing but {@code code} and {@code message} stays
 * correct. It is omitted rather than serialized as null so that the common case is exactly the two
 * documented fields.
 *
 * @param code stable identifier the client branches on
 * @param message English, developer-facing, for logs -- never shown to an end user
 * @param errors per-field detail, present only when {@code code} is {@code VALIDATION_FAILED}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, List<FieldError> errors) {

  public ErrorResponse(String code, String message) {
    this(code, message, null);
  }

  /**
   * One rejected field.
   *
   * @param field JSON path of the offending property, in the same snake_case the request used
   * @param code one of REQUIRED, PATTERN, SIZE, RANGE, FORMAT, ENUM
   * @param message English description of the constraint that failed
   */
  public record FieldError(String field, String code, String message) {}
}
