// Spring Boot 4.1 (Spring Framework 7.0). The same failure, handled per controller.
// Two endpoints, one exception, two bodies -- and a third that leaks the detail.
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class ErrorShapeDriftDemo {

  static class NotFound extends RuntimeException {
    NotFound(String message) {
      super(message);
    }
  }

  @RestController
  static class TrackController {
    @GetMapping("/api/v1/tracks/x")
    String get() {
      throw new NotFound("track 'x' does not exist");
    }

    // Written first, by someone who needed an error body that afternoon.
    @ExceptionHandler(NotFound.class)
    ResponseEntity<Map<String, Object>> handle(NotFound failure) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("error", failure.getMessage());
      return ResponseEntity.status(404).body(body);
    }
  }

  @RestController
  static class LessonController {
    @GetMapping("/api/v1/lessons/y")
    String get() {
      throw new NotFound("lesson 'y' does not exist");
    }

    // Written later, by someone who had not seen the first one.
    @ExceptionHandler(NotFound.class)
    ResponseEntity<Map<String, Object>> handle(NotFound failure) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("status", 404);
      body.put("detail", failure.getMessage());
      body.put("type", "not_found");
      return ResponseEntity.status(404).body(body);
    }
  }

  @RestController
  static class BlogController {
    @GetMapping("/api/v1/blog/z")
    String get() {
      // Nobody wrote a handler for this one at all.
      throw new IllegalStateException(
          "connection to db-primary.internal:5432 refused for user bytelore_app");
    }
  }

  static void quietLogging() {
    ((ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
  }

  static void call(Object controller, String path) {
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    try {
      MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get(path)).andReturn();
      System.out.println(
          path + " -> " + result.getResponse().getStatus() + " " + result.getResponse().getContentAsString());
    } catch (Exception escaped) {
      Throwable cause = escaped.getCause() == null ? escaped : escaped.getCause();
      System.out.println(path + " -> escaped as " + cause.getClass().getSimpleName());
      System.out.println("   carrying: " + cause.getMessage());
    }
  }

  public static void main(String[] args) {
    quietLogging();

    for (Object[] endpoint :
        List.of(
            new Object[] {new TrackController(), "/api/v1/tracks/x"},
            new Object[] {new LessonController(), "/api/v1/lessons/y"},
            new Object[] {new BlogController(), "/api/v1/blog/z"})) {
      call(endpoint[0], (String) endpoint[1]);
    }
  }
}
