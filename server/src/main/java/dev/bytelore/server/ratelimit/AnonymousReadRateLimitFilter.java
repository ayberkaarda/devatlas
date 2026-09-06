package dev.bytelore.server.ratelimit;

import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.security.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP throttling for the two anonymous endpoint families the content sync protocol owns (§3.6,
 * §3.7 of the REST contract).
 *
 * <p>These endpoints take no credentials, so there is no principal to key a limit by and the client
 * IP is what is left. That is a weaker key than a user id -- a shared exit address makes an office
 * look like one caller -- but it is the key the contract specifies, and the alternative was
 * authentication, which §3.7 rules out for a reason that has nothing to do with abuse.
 *
 * <p>A rejection is a {@code 429} with {@code Retry-After} in seconds. The download engine treats
 * that as a wait rather than a package failure: it does not consume one of the entity's three
 * attempts (§11), because a server that is protecting itself has not said anything about the
 * content.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AnonymousReadRateLimitFilter extends OncePerRequestFilter {

  private static final String MANIFEST_PREFIX = "/api/v1/manifest/";
  private static final String CONTENT_PREFIX = "/api/v1/content/";
  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final FixedWindowRateLimiter limiter;
  private final RateLimitProperties properties;
  private final ErrorResponseWriter errorWriter;

  public AnonymousReadRateLimitFilter(
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
    Scope scope = scopeOf(request);
    if (scope == null) {
      chain.doFilter(request, response);
      return;
    }

    // request.getRemoteAddr(), not X-Forwarded-For. A forwarded header is caller-supplied unless a
    // trusted proxy is configured to rewrite it, and trusting it unconditionally would let anyone
    // mint a fresh bucket per request simply by varying the value -- a rate limiter that any client
    // can opt out of.
    String bucketKey = scope.name() + ":" + request.getRemoteAddr();
    FixedWindowRateLimiter.Decision decision = limiter.record(bucketKey, scope.limit(), WINDOW);
    if (decision.allowed()) {
      chain.doFilter(request, response);
      return;
    }

    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
    errorWriter.write(
        response,
        ErrorCode.RATE_LIMITED,
        "Too many requests; retry in %d second(s).".formatted(decision.retryAfterSeconds()));
  }

  private Scope scopeOf(HttpServletRequest request) {
    if (!HttpMethod.GET.matches(request.getMethod())) {
      return null;
    }
    String path = request.getRequestURI();
    if (path == null) {
      return null;
    }
    if (path.startsWith(MANIFEST_PREFIX)) {
      return new Scope("manifest", properties.getManifestPerMinute());
    }
    if (path.startsWith(CONTENT_PREFIX)) {
      return new Scope("content", properties.getContentPerMinute());
    }
    return null;
  }

  private record Scope(String name, int limit) {}
}
