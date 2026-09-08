// Spring Boot 4.1 (Spring Framework 7.0). What the web layer does decide: the shape
// of the input it will accept, and the status code it reports. Nothing else.
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ControllerBoundaryDemo {

  record CreateTrackRequest(@NotBlank @Size(max = 40) String slug, @NotBlank String title) {}

  static final AtomicInteger serviceCalls = new AtomicInteger();

  static class TrackService {
    String create(String slug, String title) {
      serviceCalls.incrementAndGet();
      return slug + " / " + title;
    }
  }

  @RestController
  static class TrackController {
    private final TrackService service;

    TrackController(TrackService service) {
      this.service = service;
    }

    @PostMapping(path = "/api/v1/tracks", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> create(@Valid @RequestBody CreateTrackRequest request) {
      return ResponseEntity.status(201).body(service.create(request.slug(), request.title()));
    }
  }

  static void quietLogging() {
    ((ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
  }

  static void post(MockMvc mockMvc, String label, String body) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/api/v1/tracks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andReturn();
    System.out.println(
        label
            + ": status="
            + result.getResponse().getStatus()
            + " serviceCalls="
            + serviceCalls.get());
  }

  public static void main(String[] args) throws Exception {
    quietLogging();

    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TrackController(new TrackService())).build();

    post(mockMvc, "valid       ", "{\"slug\":\"spring-boot-path\",\"title\":\"The Spring Boot Path\"}");
    post(mockMvc, "blank slug  ", "{\"slug\":\"\",\"title\":\"The Spring Boot Path\"}");
    post(mockMvc, "missing body", "{\"title\":\"The Spring Boot Path\"}");
    post(mockMvc, "not json    ", "not json at all");
  }
}
