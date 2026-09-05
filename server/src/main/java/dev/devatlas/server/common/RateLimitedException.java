package dev.devatlas.server.common;

/**
 * A caller exceeded a budget enforced outside the anonymous manifest/content endpoints -- currently
 * only the manual whitelist source fetch trigger (§3.6, §5.7 of the REST contract), which is
 * limited to 6 requests per hour per source.
 *
 * <p>Carries {@code retryAfterSeconds} so the {@code Retry-After} header can be set on the
 * response, the same contract the anonymous rate limiter honours.
 */
public class RateLimitedException extends ApiException {

  private static final long serialVersionUID = 1L;

  private final long retryAfterSeconds;

  public RateLimitedException(String message, long retryAfterSeconds) {
    super(ErrorCode.RATE_LIMITED, message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
