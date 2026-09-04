package dev.devatlas.server.common;

/**
 * One rejected field, carried by an {@link ApiException} that needs the {@code errors[]} extension
 * (§6) but is not raised through ordinary Bean Validation -- a class-level rule that depends on
 * something outside the request body, such as the translation {@code body} requirement of §5.6,
 * which reads the {@code entityType} path variable no request-DTO annotation can see.
 *
 * <p>{@link dev.devatlas.server.web.GlobalExceptionHandler} converts this into the wire-facing
 * {@code ErrorResponse.FieldError}; this type lives in {@code common} rather than {@code web} so
 * that {@code common} never has to depend on the web layer to describe one.
 *
 * @param field JSON path of the offending property, in snake_case
 * @param code one of REQUIRED, PATTERN, SIZE, RANGE, FORMAT, ENUM
 * @param message English description of the constraint that failed
 */
public record ApiFieldError(String field, String code, String message) {}
