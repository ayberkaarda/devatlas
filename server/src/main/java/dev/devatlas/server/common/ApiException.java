package dev.devatlas.server.common;

/**
 * The one exception type the API layer throws when it wants a specific error code on the wire.
 *
 * <p>Controllers never build an error body by hand: they throw this, and a single advice turns it
 * into {@code {code, message}} with the status the code carries.
 */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode code;

  public ApiException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ApiException(ErrorCode code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
