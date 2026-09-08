// Spring Boot 4.1 (Spring Framework 7.0). One advice, one body shape. Three
// different failures, and a client that can branch on all of them the same way.
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
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

class ErrorContractDemo {

  /** The closed set of codes the API exposes, each with the status it answers. */
  enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    TRACK_NOT_FOUND(HttpStatus.NOT_FOUND),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    final HttpStatus status;

    ErrorCode(HttpStatus status) {
      this.status = status;
    }
  }

  record ErrorBody(String code, String message, List<String> fields) {}

  static class ApiException extends RuntimeException {
    final ErrorCode code;

    ApiException(ErrorCode code, String message) {
      super(message);
      this.code = code;
    }
  }

  record EnrolRequest(@NotBlank String trackSlug) {}

  @RestController
  static class EnrolmentController {
    @PostMapping(path = "/api/v1/enrolments", consumes = MediaType.APPLICATION_JSON_VALUE)
    String enrol(@Valid @RequestBody EnrolRequest request) {
      if (request.trackSlug().equals("no-such-track")) {
        throw new ApiException(ErrorCode.TRACK_NOT_FOUND, "No track with that slug.");
      }
      if (request.trackSlug().equals("boom")) {
        // Something nobody anticipated: a null pointer, a driver error, a bug.
        throw new IllegalStateException(
            "connection to db-primary.internal:5432 refused for user bytelore_app");
      }
      return "ok";
    }
  }

  /** The only place in the application where an error body is built. */
  @RestControllerAdvice
  static class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> handleApi(ApiException failure) {
      return ResponseEntity.status(failure.code.status)
          .body(new ErrorBody(failure.code.name(), failure.getMessage(), List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> handleValidation(MethodArgumentNotValidException failure) {
      List<String> fields =
          failure.getBindingResult().getFieldErrors().stream()
              .map(error -> error.getField() + ":" + error.getCode())
              .sorted()
              .toList();
      return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status)
          .body(
              new ErrorBody(
                  ErrorCode.VALIDATION_FAILED.name(),
                  "Request validation failed for " + fields.size() + " field(s).",
                  fields));
    }

    // The catch-all. The detail goes to the log; the body says only that something
    // failed, because a caller that can read an internal hostname out of an error
    // body is being handed a map of the estate.
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> handleUnexpected(Exception failure) {
      return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status)
          .body(new ErrorBody(ErrorCode.INTERNAL_ERROR.name(), "An internal error occurred.", List.of()));
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
        MockMvcBuilders.standaloneSetup(new EnrolmentController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    for (String body :
        List.of(
            "{\"trackSlug\":\"spring-boot-path\"}",
            "{\"trackSlug\":\"no-such-track\"}",
            "{\"trackSlug\":\"\"}",
            "{\"trackSlug\":\"boom\"}")) {
      MvcResult result =
          mockMvc
              .perform(
                  MockMvcRequestBuilders.post("/api/v1/enrolments")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body))
              .andReturn();
      System.out.println(
          result.getResponse().getStatus() + " " + result.getResponse().getContentAsString());
    }
  }
}
