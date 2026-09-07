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
 * Per-address throttling for the two authentication endpoints whose budget is keyed by nothing but
 * the connection (§3.6 of the REST contract): {@code POST /auth/register} and {@code POST
 * /auth/logout}.
 *
 * <p><strong>Which layer a limit lives in is decided by its key.</strong> A key made only of the
 * connection can be read before anything else happens, so it belongs in a filter, and rejecting
 * there does the least work per rejected request -- no body parsed, no transaction opened, no
 * password hashed. The three remaining authentication limits are keyed by something that does not
 * exist yet at this point: sign-in is keyed partly by an email address that is still unparsed
 * bytes, and token exchange and password change are keyed by a user who is only known once the
 * presented credential has been resolved. Those are enforced where that value first exists, in
 * {@code AuthService}, rather than by teaching this filter to read request bodies.
 *
 * <p>Registered as a bean, so the servlet container runs it ahead of the security chain. That is
 * the opposite of the placement the progress sync limiter needs and for the opposite reason: these
 * endpoints are unauthenticated by design, so there is no principal to wait for and nothing is
 * gained by running later.
 *
 * <p>Both budgets are deliberately coarse. Sign-out is unauthenticated because a person whose
 * access token expired while offline must still be able to sign out, and account creation is the
 * front door; a limit tight enough to matter to an attacker with a botnet would be tight enough to
 * break a family or an office sharing one address.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 21)
public class AnonymousAuthRateLimitFilter extends OncePerRequestFilter {

  private static final String REGISTER_PATH = "/api/v1/auth/register";
  private static final String LOGOUT_PATH = "/api/v1/auth/logout";
  private static final Duration WINDOW = Duration.ofHours(1);

  private final FixedWindowRateLimiter limiter;
  private final RateLimitProperties properties;
  private final ErrorResponseWriter errorWriter;

  public AnonymousAuthRateLimitFilter(
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

    // request.getRemoteAddr(), not X-Forwarded-For, for the same reason the anonymous read limiter
    // gives: a forwarded header is caller-supplied unless a trusted proxy rewrites it, and
    // honouring it unconditionally is a rate limiter any client can opt out of by varying a string.
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
    if (!HttpMethod.POST.matches(request.getMethod())) {
      return null;
    }
    String path = request.getRequestURI();
    if (REGISTER_PATH.equals(path)) {
      return new Scope("auth-register", properties.getRegisterPerHour());
    }
    if (LOGOUT_PATH.equals(path)) {
      return new Scope("auth-logout", properties.getLogoutPerHour());
    }
    return null;
  }

  private record Scope(String name, int limit) {}
}
