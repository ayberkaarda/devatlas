package dev.bytelore.server.common;

import java.util.List;

/**
 * The one exception type the API layer throws when it wants a specific error code on the wire.
 *
 * <p>Controllers never build an error body by hand: they throw this, and a single advice turns it
 * into {@code {code, message}} with the status the code carries.
 *
 * <p>{@code fieldErrors} carries the optional {@code errors[]} extension (§6) for the handful of
 * validation rules that are class-level and depend on more than the request body alone -- the
 * translation {@code body} requirement of §5.6 reads the {@code entityType} path variable, which no
 * annotation on the request DTO can see -- so the service layer raises them here instead of through
 * ordinary Bean Validation, with the same {@code errors[].field = "body"} shape a client would get
 * from either path. {@code null} (the common case) means no field-level detail.
 */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode code;
  private final List<ApiFieldError> fieldErrors;

  public ApiException(ErrorCode code, String message) {
    super(message);
    this.code = code;
    this.fieldErrors = null;
  }

  public ApiException(ErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.fieldErrors = null;
  }

  public ApiException(ErrorCode code, String message, List<ApiFieldError> fieldErrors) {
    super(message);
    this.code = code;
    this.fieldErrors = fieldErrors;
  }

  public ErrorCode code() {
    return code;
  }

  public List<ApiFieldError> fieldErrors() {
    return fieldErrors;
  }
}
