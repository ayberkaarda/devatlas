package dev.devatlas.server.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Echoes a caller-supplied correlation identifier and puts it into the logging context.
 *
 * <p>It never appears in a response body. Its whole job is to let a report of "this call failed" be
 * matched against the server-side record of that exact call, including the security events that are
 * logged rather than returned.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-Request-Id";
  private static final String MDC_KEY = "requestId";
  private static final int MAX_LENGTH = 128;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = request.getHeader(HEADER);
    if (requestId != null && !requestId.isBlank()) {
      // Truncated and stripped of control characters: this value is attacker-controlled and ends up
      // in a response header and in log lines, both of which are injectable with the right bytes.
      String sanitized = requestId.replaceAll("[\\p{Cntrl}]", "");
      if (sanitized.length() > MAX_LENGTH) {
        sanitized = sanitized.substring(0, MAX_LENGTH);
      }
      if (!sanitized.isEmpty()) {
        response.setHeader(HEADER, sanitized);
        MDC.put(MDC_KEY, sanitized);
      }
    }
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
