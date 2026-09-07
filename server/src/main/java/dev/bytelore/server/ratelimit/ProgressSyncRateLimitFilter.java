package dev.bytelore.server.ratelimit;

import dev.bytelore.server.auth.AccessTokenClaims;
import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.security.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-user throttling on progress sync (§3.6 of the REST contract): 120 requests per hour.
 *
 * <p>Same counter as the anonymous read limiter next door -- one fixed-window row in PostgreSQL,
 * decided in a single upsert -- with the one difference that matters: the bucket is keyed by the
 * authenticated user id rather than by the client address. These endpoints have a principal, and a
 * principal is the better key in both directions. It does not punish an office or a campus for
 * sharing an exit address, and it cannot be shed by moving to a different network.
 *
 * <p>Enforced in the filter chain rather than inside the service for a reason that is easy to get
 * wrong: the counter is a database write, and a check made inside the request's own transaction is
 * a check that gets rolled back whenever the request fails. A caller could then burn an unbounded
 * number of failing requests without the count ever surviving. Deciding before the transaction
 * opens keeps a rejected attempt counted.
 *
 * <p>The filter runs after authentication and looks only at what authentication left behind. A
 * request with no valid token is passed straight through, so an unauthenticated caller is answered
 * by the security chain with {@code AUTH_REQUIRED} rather than being told it is over a budget it
 * never had.
 *
 * <p>The two directions hold separate budgets. They are separate endpoints with separate limits,
 * and a client that polls for another device's progress must not thereby lose its ability to upload
 * its own -- the failure that merging them would produce is a device that silently stops syncing
 * because it was reading too often.
 */
public class ProgressSyncRateLimitFilter extends OncePerRequestFilter {

  private static final String PROGRESS_PATH = "/api/v1/sync/progress";
  private static final Duration WINDOW = Duration.ofHours(1);
  private static final String PUSH_SCOPE = "sync-progress-push";
  private static final String PULL_SCOPE = "sync-progress-pull";

  private final FixedWindowRateLimiter limiter;
  private final RateLimitProperties properties;
  private final ErrorResponseWriter errorWriter;

  public ProgressSyncRateLimitFilter(
      FixedWindowRateLimiter limiter,
      RateLimitProperties properties,
      ErrorResponseWriter errorWriter) {
    this.limiter = limiter;
    this.properties = properties;
    this.errorWriter = errorWriter;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String scope = scopeOf(request);
    AccessTokenClaims caller = authenticatedCaller();
    if (scope == null || caller == null) {
      chain.doFilter(request, response);
      return;
    }

    String bucketKey = scope + ":" + caller.userId();
    FixedWindowRateLimiter.Decision decision =
        limiter.record(bucketKey, properties.getSyncProgressPerHour(), WINDOW);
    if (decision.allowed()) {
      chain.doFilter(request, response);
      return;
    }

    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
    errorWriter.write(
        response,
        ErrorCode.RATE_LIMITED,
        "Too many progress sync requests; retry in %d second(s)."
            .formatted(decision.retryAfterSeconds()));
  }

  private static String scopeOf(HttpServletRequest request) {
    if (!PROGRESS_PATH.equals(request.getRequestURI())) {
      return null;
    }
    if (HttpMethod.POST.matches(request.getMethod())) {
      return PUSH_SCOPE;
    }
    if (HttpMethod.GET.matches(request.getMethod())) {
      return PULL_SCOPE;
    }
    // Any other verb is not an endpoint at all; the dispatcher answers METHOD_NOT_ALLOWED and
    // there is no budget to spend.
    return null;
  }

  private static AccessTokenClaims authenticatedCaller() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getPrincipal() instanceof AccessTokenClaims claims) {
      return claims;
    }
    return null;
  }
}
