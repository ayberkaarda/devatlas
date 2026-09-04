package dev.devatlas.server.security;

import dev.devatlas.server.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Answers an authenticated request whose role is insufficient.
 *
 * <p>Reported honestly as a role failure. The API never answers "not found" to hide the existence
 * of an administrative resource from a signed-in caller: a caller who is told the wrong thing
 * debugs the wrong problem, and the resource's existence was never the secret.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

  private final ErrorResponseWriter writer;

  public RestAccessDeniedHandler(ErrorResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    writer.write(
        response, ErrorCode.FORBIDDEN_ROLE, "The authenticated role may not perform this action.");
  }
}
