// Spring Boot 4.1 (Spring Framework 7.0). Field errors translated into the wire's
// own vocabulary: a closed set of per-field codes and snake_case property paths.
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

class FieldErrorContractDemo {

  record CodeExample(@NotBlank String language, @NotBlank String caption) {}

  record CreateLessonRequest(
      @NotBlank @Size(max = 80) String slug,
      @Min(1) int estimatedMinutes,
      @Valid List<CodeExample> codeExamples) {}

  record FieldProblem(String field, String code) {}

  record ErrorBody(String code, String message, List<FieldProblem> errors) {}

  /**
   * Bean Validation constraint names mapped onto the closed set of per-field codes
   * the contract exposes. Anything unlisted degrades to FORMAT rather than inventing
   * a code no client has been told about.
   */
  static final Map<String, String> CONSTRAINT_CODES =
      Map.of("NotNull", "REQUIRED", "NotBlank", "REQUIRED", "Size", "SIZE", "Min", "RANGE");

  /** camelCase property path to snake_case, leaving indexes and separators alone. */
  static String toSnakeCasePath(String path) {
    StringBuilder result = new StringBuilder(path.length() + 8);
    for (int i = 0; i < path.length(); i++) {
      char c = path.charAt(i);
      if (Character.isUpperCase(c)) {
        result.append('_').append(Character.toLowerCase(c));
      } else {
        result.append(c);
      }
    }
    return result.toString().toLowerCase(Locale.ROOT);
  }

  @RestController
  static class LessonController {
    @PostMapping(path = "/api/v1/lessons", consumes = MediaType.APPLICATION_JSON_VALUE)
    String create(@Valid @RequestBody CreateLessonRequest request) {
      return "created";
    }
  }

  @RestControllerAdvice
  static class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException failure) {
      List<FieldProblem> problems =
          failure.getBindingResult().getFieldErrors().stream()
              .map(
                  error ->
                      new FieldProblem(
                          toSnakeCasePath(error.getField()),
                          CONSTRAINT_CODES.getOrDefault(error.getCode(), "FORMAT")))
              .sorted((a, b) -> a.field().compareTo(b.field()))
              .toList();
      return ResponseEntity.badRequest()
          .body(
              new ErrorBody(
                  "VALIDATION_FAILED",
                  "Request validation failed for " + problems.size() + " field(s).",
                  problems));
    }
  }

  static void quietLogging() {
    ((ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
  }

  public static void main(String[] args) throws Exception {
    quietLogging();

    MockMvc mockMvc =
        MockMvcBuilders.standaloneSetup(new LessonController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    String body =
        """
        {"slug":"","estimatedMinutes":0,
         "codeExamples":[{"language":"java","caption":"ok"},{"language":"","caption":""}]}
        """;

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/api/v1/lessons")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn();

    System.out.println("status: " + result.getResponse().getStatus());
    System.out.println("body:   " + result.getResponse().getContentAsString());
  }
}
