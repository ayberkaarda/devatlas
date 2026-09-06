package dev.bytelore.server.web;

import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.ApiFieldError;
import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.common.RateLimitedException;
import dev.bytelore.server.content.manifest.ContentVersionSupersededException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns every exception that escapes a controller into the {@code {code, message}} envelope.
 *
 * <p>This is the only place an error body is built. No controller assembles one by hand, and no
 * unmapped exception is allowed to leave as a framework default body -- an error whose shape
 * depends on which layer threw is an error three client codebases cannot branch on.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /**
   * Bean Validation constraint names mapped to the closed set of per-field codes the contract
   * exposes. Anything unlisted degrades to FORMAT rather than inventing a new code.
   */
  private static final Map<String, String> CONSTRAINT_CODES =
      Map.ofEntries(
          Map.entry("NotNull", "REQUIRED"),
          Map.entry("NotBlank", "REQUIRED"),
          Map.entry("NotEmpty", "REQUIRED"),
          Map.entry("Pattern", "PATTERN"),
          Map.entry("Size", "SIZE"),
          Map.entry("Length", "SIZE"),
          Map.entry("Min", "RANGE"),
          Map.entry("Max", "RANGE"),
          Map.entry("DecimalMin", "RANGE"),
          Map.entry("DecimalMax", "RANGE"),
          Map.entry("Positive", "RANGE"),
          Map.entry("PositiveOrZero", "RANGE"),
          Map.entry("Range", "RANGE"),
          Map.entry("Email", "FORMAT"),
          Map.entry("URL", "FORMAT"));

  /**
   * The one error that carries a number the client acts on rather than logs. A superseded package
   * request is not a failure the download engine retries -- it re-plans against the version named
   * here and does not consume an attempt.
   */
  @ExceptionHandler(ContentVersionSupersededException.class)
  public ResponseEntity<ErrorResponse> handleSupersededVersion(
      ContentVersionSupersededException exception) {
    return ResponseEntity.status(exception.code().status())
        .body(
            new ErrorResponse(
                exception.code().name(), exception.getMessage(), null, exception.currentVersion()));
  }

  /**
   * The one error whose response carries a header a caller is expected to honour rather than just a
   * body: {@code Retry-After}, in seconds, matching the convention the anonymous manifest/content
   * rate limiter already uses (§3.6).
   */
  @ExceptionHandler(RateLimitedException.class)
  public ResponseEntity<ErrorResponse> handleRateLimited(RateLimitedException exception) {
    return ResponseEntity.status(exception.code().status())
        .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
        .body(new ErrorResponse(exception.code().name(), exception.getMessage()));
  }

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
    List<ApiFieldError> fieldErrors = exception.fieldErrors();
    if (fieldErrors == null || fieldErrors.isEmpty()) {
      return respond(exception.code(), exception.getMessage());
    }
    List<ErrorResponse.FieldError> errors =
        fieldErrors.stream()
            .map(e -> new ErrorResponse.FieldError(e.field(), e.code(), e.message()))
            .toList();
    return ResponseEntity.status(exception.code().status())
        .body(new ErrorResponse(exception.code().name(), exception.getMessage(), errors));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
    List<ErrorResponse.FieldError> fieldErrors = new ArrayList<>();
    for (org.springframework.validation.FieldError error :
        exception.getBindingResult().getFieldErrors()) {
      fieldErrors.add(
          new ErrorResponse.FieldError(
              toSnakeCasePath(error.getField()),
              CONSTRAINT_CODES.getOrDefault(error.getCode(), "FORMAT"),
              error.getDefaultMessage()));
    }
    for (org.springframework.validation.ObjectError error :
        exception.getBindingResult().getGlobalErrors()) {
      // Class-level constraints have no single offending field; they are reported against the
      // object they were declared on so the client still learns which payload was rejected.
      fieldErrors.add(
          new ErrorResponse.FieldError(
              toSnakeCasePath(error.getObjectName()),
              CONSTRAINT_CODES.getOrDefault(error.getCode(), "FORMAT"),
              error.getDefaultMessage()));
    }
    String message =
        "Request validation failed for %d field%s."
            .formatted(fieldErrors.size(), fieldErrors.size() == 1 ? "" : "s");
    return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
        .body(new ErrorResponse(ErrorCode.VALIDATION_FAILED.name(), message, fieldErrors));
  }

  /**
   * A body that could not be parsed at all. A deserializer is allowed to raise an {@link
   * ApiException} of its own -- a timestamp with no offset, say -- and that code must survive
   * Jackson wrapping it, so the cause chain is unwrapped before falling back.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception) {
    Throwable cause = exception.getCause();
    while (cause != null) {
      if (cause instanceof ApiException apiException) {
        return respond(apiException.code(), apiException.getMessage());
      }
      cause = cause.getCause();
    }
    return respond(ErrorCode.MALFORMED_REQUEST, "Request body is not readable as JSON.");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMediaType(
      HttpMediaTypeNotSupportedException exception) {
    return respond(
        ErrorCode.UNSUPPORTED_MEDIA_TYPE,
        "Content type '%s' is not supported; use application/json."
            .formatted(String.valueOf(exception.getContentType())));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethod(
      HttpRequestMethodNotSupportedException exception) {
    return respond(
        ErrorCode.METHOD_NOT_ALLOWED,
        "Method %s is not supported on this path.".formatted(exception.getMethod()));
  }

  @ExceptionHandler({
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class
  })
  public ResponseEntity<ErrorResponse> handleParameter(Exception exception) {
    return respond(ErrorCode.INVALID_PARAMETER, exception.getMessage());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ErrorResponse> handleOptimisticLock(
      OptimisticLockingFailureException exception) {
    return respond(
        ErrorCode.VERSION_CONFLICT,
        "The resource was modified concurrently; re-read it and retry.");
  }

  /**
   * A database constraint rejected a write that reached the database at all -- meaning the
   * service-layer validation meant to catch it did not (line 1720 of the REST contract: "database
   * constraints mirror them so a bypass fails loudly rather than storing bad data"). {@code
   * DataIntegrityViolationException} is Spring's own umbrella for this family (a check constraint,
   * a foreign key, a unique index, a not-null column), so this one handler is the fail-loud half of
   * that promise for whichever mirrored constraint the service layer's own check missed.
   *
   * <p>Mapped to the existing {@code VALIDATION_FAILED} code rather than a new one: from the
   * client's perspective, a write refused because the data was invalid is the same category of
   * failure regardless of which layer caught it, and inventing a second code for "the database
   * caught it instead" would be a distinction with no client-observable use. The full driver
   * message goes to the log, not the response -- it can name a column, a constraint or a literal
   * value from the request, none of which belongs in a body sent back over the wire.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
      DataIntegrityViolationException exception) {
    log.warn("Database constraint violation escaped to the API boundary.", exception);
    return respond(
        ErrorCode.VALIDATION_FAILED,
        "The request violates a stored data constraint and cannot be saved as given.");
  }

  /**
   * Method-level authorization failures. An authenticated caller with an insufficient role is told
   * so honestly with 403; the API never answers 404 to hide the existence of an admin resource.
   */
  @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
  public ResponseEntity<ErrorResponse> handleAccessDenied(RuntimeException exception) {
    return respond(ErrorCode.FORBIDDEN_ROLE, "The authenticated role may not perform this action.");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
    // The detail belongs in the log, correlated by request id -- not in a response body that a
    // caller could mine for internals.
    log.error("Unhandled exception", exception);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse(ErrorCode.INTERNAL_ERROR.name(), "An internal error occurred."));
  }

  private static ResponseEntity<ErrorResponse> respond(ErrorCode code, String message) {
    return ResponseEntity.status(code.status())
        .body(new ErrorResponse(code.name(), message == null ? code.name() : message));
  }

  /**
   * Converts a camelCase property path to the snake_case the wire uses, leaving array indexes and
   * path separators alone: {@code codeExamples[0].language} becomes {@code
   * code_examples[0].language}.
   */
  static String toSnakeCasePath(String path) {
    if (path == null || path.isEmpty()) {
      return path;
    }
    StringBuilder result = new StringBuilder(path.length() + 8);
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if (Character.isUpperCase(c)) {
        result.append('_').append(Character.toLowerCase(c));
      } else {
        result.append(c);
      }
    }
    return result.toString().toLowerCase(Locale.ROOT);
  }
}
