package dev.devatlas.server.security;

import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.web.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the standard error envelope from inside the filter chain.
 *
 * <p>Authentication and authorization failures are decided before any controller runs, so the
 * advice that normally builds error bodies never sees them. Without this, those two responses would
 * be the only ones on the API with a different shape -- which is exactly the case a client is least
 * likely to have handled.
 */
@Component
public class ErrorResponseWriter {

  private final ObjectMapper objectMapper;

  public ErrorResponseWriter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void write(HttpServletResponse response, ErrorCode code, String message)
      throws IOException {
    response.setStatus(code.status().value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response
        .getWriter()
        .write(objectMapper.writeValueAsString(new ErrorResponse(code.name(), message)));
  }
}
