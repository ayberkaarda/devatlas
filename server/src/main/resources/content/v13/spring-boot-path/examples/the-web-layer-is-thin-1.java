// Spring Boot 4.1 (Spring Framework 7.0). A controller that binds, delegates and
// shapes a response, and a service that owns the one rule in this program.
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ThinControllerDemo {

  record EnrolRequest(@NotBlank String trackSlug) {}

  record EnrolResponse(String trackSlug, int seatsLeft) {}

  static class SeatLimitReached extends RuntimeException {
    SeatLimitReached(String message) {
      super(message);
    }
  }

  // The rule lives here, once, and applies to every caller of this method --
  // an HTTP request, a scheduled job, a migration, a test.
  static class EnrolmentService {
    private final Map<String, Integer> seats = new LinkedHashMap<>(Map.of("spring-boot-path", 1));

    EnrolResponse enrol(String trackSlug) {
      int left = seats.getOrDefault(trackSlug, 0);
      if (left <= 0) {
        throw new SeatLimitReached("No seats left on " + trackSlug);
      }
      seats.put(trackSlug, left - 1);
      return new EnrolResponse(trackSlug, left - 1);
    }
  }

  @RestController
  static class EnrolmentController {
    private final EnrolmentService service;

    EnrolmentController(EnrolmentService service) {
      this.service = service;
    }

    // Bind, delegate, choose a status. No branch here decides who may enrol.
    @PostMapping(path = "/api/v1/enrolments", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<EnrolResponse> enrol(@Valid @RequestBody EnrolRequest request) {
      return ResponseEntity.status(201).body(service.enrol(request.trackSlug()));
    }
  }

  // Logback is Spring Boot's default logging back end; it is switched off so the
  // output below is only what this program prints.
  static void quietLogging() {
    ((ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
  }

  public static void main(String[] args) throws Exception {
    quietLogging();

    EnrolmentService service = new EnrolmentService();
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new EnrolmentController(service)).build();

    MvcResult first =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/api/v1/enrolments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"trackSlug\":\"spring-boot-path\"}"))
            .andReturn();
    System.out.println("first request status: " + first.getResponse().getStatus());
    System.out.println("first request body: " + first.getResponse().getContentAsString());

    // The seat is gone, so the second request must be refused -- by the service.
    try {
      mockMvc
          .perform(
              MockMvcRequestBuilders.post("/api/v1/enrolments")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"trackSlug\":\"spring-boot-path\"}"))
          .andReturn();
      System.out.println("second request: not refused, which should not happen");
    } catch (Exception thrown) {
      Throwable cause = thrown.getCause() == null ? thrown : thrown.getCause();
      System.out.println("second request refused by: " + cause.getClass().getSimpleName());
    }

    // The same rule, reached without HTTP at all.
    try {
      new EnrolmentService().enrol("typescript-path");
    } catch (SeatLimitReached refused) {
      System.out.println("service called directly refused: " + refused.getMessage());
    }
  }
}
