package dev.devatlas.server.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The body of every non-2xx response.
 *
 * <p>Two additive extensions exist, and both are omitted rather than serialized as null, so the
 * common case is exactly the two documented fields and a client that reads nothing but {@code code}
 * and {@code message} stays correct.
 *
 * <p>{@code errors} carries per-field detail on validation failures. {@code current_version}
 * carries the entity's current {@code content_version} on {@code CONTENT_VERSION_SUPERSEDED}: the
 * content sync protocol requires a client that asked for a superseded package to be able to re-plan
 * without a second round trip, and re-planning needs that number. Putting it in the envelope keeps
 * the single advice as the only place an error body is built -- the alternative, a bespoke body for
 * one status, would be the one response shape three client codebases had not handled.
 *
 * @param code stable identifier the client branches on
 * @param message English, developer-facing, for logs -- never shown to an end user
 * @param errors per-field detail, present only when {@code code} is {@code VALIDATION_FAILED}
 * @param currentVersion the entity's current content version, present only when {@code code} is
 *     {@code CONTENT_VERSION_SUPERSEDED}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String code, String message, List<FieldError> errors, Integer currentVersion) {

  public ErrorResponse(String code, String message) {
    this(code, message, null, null);
  }

  public ErrorResponse(String code, String message, List<FieldError> errors) {
    this(code, message, errors, null);
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
