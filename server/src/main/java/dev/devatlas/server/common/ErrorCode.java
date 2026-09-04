package dev.devatlas.server.common;

import org.springframework.http.HttpStatus;

/**
 * The complete error code catalogue of the HTTP API.
 *
 * <p>These identifiers are public API surface. Clients map a code to a translation key and branch
 * on it, so <strong>a code is never renamed once it has shipped</strong>: it is deprecated
 * alongside a successor instead. The accompanying {@code message} is English, developer-facing, and
 * meant for logs -- clients never display it and never parse it.
 *
 * <p>The enum is the whole catalogue, not only the codes the current endpoints can raise. The code
 * namespace is shared across every part of the API, and holding it in one place is what stops the
 * same identifier being invented twice with two different meanings.
 */
public enum ErrorCode {

  // Request and validation.
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
  INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
  UNSUPPORTED_LOCALE(HttpStatus.BAD_REQUEST),
  UNSUPPORTED_LANGUAGE(HttpStatus.BAD_REQUEST),
  ENTITY_TYPE_UNSUPPORTED(HttpStatus.BAD_REQUEST),
  INVALID_SORT_FIELD(HttpStatus.BAD_REQUEST),
  PAGE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST),
  DUPLICATE_ITEM_IN_BATCH(HttpStatus.BAD_REQUEST),
  AMBIGUOUS_TOKEN_DELIVERY(HttpStatus.BAD_REQUEST),
  // 413. The framework constant was renamed to match the current HTTP specification;
  // the wire status and these code names are unchanged.
  PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE),
  SYNC_BATCH_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

  // Authentication.
  AUTH_REQUIRED(HttpStatus.UNAUTHORIZED),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
  ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
  ACCESS_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
  REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
  REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
  REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED),

  // Authorization.
  FORBIDDEN_ROLE(HttpStatus.FORBIDDEN),
  ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
  AUTO_POST_NOT_EDITABLE(HttpStatus.FORBIDDEN),

  // Not found.
  TRACK_NOT_FOUND(HttpStatus.NOT_FOUND),
  MODULE_NOT_FOUND(HttpStatus.NOT_FOUND),
  LESSON_NOT_FOUND(HttpStatus.NOT_FOUND),
  CODE_EXAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND),
  MIND_MAP_NOT_FOUND(HttpStatus.NOT_FOUND),
  BLOG_POST_NOT_FOUND(HttpStatus.NOT_FOUND),
  TRANSLATION_NOT_FOUND(HttpStatus.NOT_FOUND),
  SOURCE_UPDATE_NOT_FOUND(HttpStatus.NOT_FOUND),
  WHITELIST_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND),

  // Conflict.
  EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
  SLUG_ALREADY_EXISTS(HttpStatus.CONFLICT),
  VERSION_CONFLICT(HttpStatus.CONFLICT),
  INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
  AUTO_POST_APPROVAL_REQUIRED(HttpStatus.CONFLICT),
  SOURCE_UPDATE_NOT_VERIFIED(HttpStatus.CONFLICT),
  PARENT_NOT_EMPTY(HttpStatus.CONFLICT),
  PUBLISHED_DELETE_BLOCKED(HttpStatus.CONFLICT),
  ORDER_SET_INCOMPLETE(HttpStatus.CONFLICT),
  PIPELINE_RUN_IN_PROGRESS(HttpStatus.CONFLICT),
  CONTENT_VERSION_SUPERSEDED(HttpStatus.CONFLICT),

  // Semantic. 422 throughout; the framework constant follows the current HTTP
  // specification's spelling of the status name.
  SANITIZED_CONTENT_EMPTY(HttpStatus.UNPROCESSABLE_CONTENT),
  AUTO_POST_SOURCE_LINK_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT),
  CANONICAL_LOCALE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT),
  MIND_MAP_INVALID(HttpStatus.UNPROCESSABLE_CONTENT),
  INSECURE_SOURCE_URL(HttpStatus.UNPROCESSABLE_CONTENT),
  INVALID_VERIFY_URL_PATTERN(HttpStatus.UNPROCESSABLE_CONTENT),
  /**
   * A password change whose {@code current_password} did not match.
   *
   * <p>Deliberately 422 rather than 401. Clients run a global interceptor that reads 401 as "the
   * access token needs refreshing" and retries; a 401 here would send that interceptor into a
   * refresh-and-retry loop against a request that can never succeed, burning the refresh budget and
   * ending in a spurious sign-out instead of "your current password is wrong". The failure is a
   * fact about the request body, not about the session, and the status has to say so.
   */
  CURRENT_PASSWORD_INCORRECT(HttpStatus.UNPROCESSABLE_CONTENT),
  CONTENT_PACKAGE_TOO_LARGE(HttpStatus.UNPROCESSABLE_CONTENT),

  // Precondition.
  CONTENT_CHANGED_DURING_RESUME(HttpStatus.PRECONDITION_FAILED),

  // Throttling and server.
  RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  /**
   * The HTTP status this code is always reported with. The mapping is fixed, never per-endpoint.
   */
  public HttpStatus status() {
    return status;
  }
}
