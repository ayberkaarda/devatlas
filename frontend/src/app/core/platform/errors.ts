/**
 * Errors the platform layer raises.
 *
 * Both carry a `code` in the same `SCREAMING_SNAKE_CASE` namespace the REST
 * API and the desktop command surface use, so the interface has one
 * error-handling path rather than one per transport: a code maps to a
 * translation key, and the developer-facing message is never shown.
 */
export class PlatformError extends Error {
  constructor(
    readonly code: string,
    message: string,
    readonly details?: unknown,
  ) {
    super(message);
    this.name = 'PlatformError';
  }
}

/**
 * Raised by the web implementation for the library operations that only exist
 * where content is stored locally.
 *
 * It throws rather than returning an empty success on purpose. A silent empty
 * array is indistinguishable from "there is genuinely nothing", and it would
 * let a bug ship looking like a legitimate empty state. In practice it is
 * never reached: the controls for a capability the platform does not have are
 * not rendered at all.
 */
export class UnsupportedOnWebError extends PlatformError {
  constructor(operation: string) {
    super(
      'UNSUPPORTED_ON_WEB',
      `The operation '${operation}' has no meaning on a client with no local store.`,
      { operation },
    );
    this.name = 'UnsupportedOnWebError';
  }
}
