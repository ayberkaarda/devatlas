package dev.devatlas.server.security;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Answers an unauthenticated request to a protected endpoint.
 *
 * <p>Three codes come out of here and a client treats each differently, so collapsing them would
 * break the clients rather than simplify the server: an expired token means refresh and retry
 * silently, an invalid one means the same but must not repeat forever, and a missing one means the
 * call was made before sign-in.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ErrorResponseWriter writer;

  public RestAuthenticationEntryPoint(ErrorResponseWriter writer) {
    this.writer = writer;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    Object failure = request.getAttribute(JwtAuthenticationFilter.AUTHENTICATION_FAILURE);
    if (failure instanceof ApiException apiException) {
      writer.write(response, apiException.code(), apiException.getMessage());
      return;
    }
    writer.write(response, ErrorCode.AUTH_REQUIRED, "This endpoint requires an access token.");
  }
}
