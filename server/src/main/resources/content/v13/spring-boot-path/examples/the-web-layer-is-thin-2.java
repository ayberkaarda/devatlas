// Spring Boot 4.1 (Spring Framework 7.0). The same rule written into the controller
// instead of the service, and the second entry point that walks straight past it.
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class FatControllerDemo {

  // The service now takes the seat unconditionally. It has no opinion about
  // whether it should.
  static class EnrolmentService {
    private final Map<String, Integer> seats = new LinkedHashMap<>(Map.of("spring-boot-path", 1));

    int takeSeat(String trackSlug) {
      int left = seats.getOrDefault(trackSlug, 0) - 1;
      seats.put(trackSlug, left);
      return left;
    }

    int seatsLeft(String trackSlug) {
      return seats.getOrDefault(trackSlug, 0);
    }
  }

  @RestController
  static class EnrolmentController {
    private final EnrolmentService service;

    EnrolmentController(EnrolmentService service) {
      this.service = service;
    }

    @PostMapping("/api/v1/enrolments/{trackSlug}")
    ResponseEntity<String> enrol(@PathVariable String trackSlug) {
      // The rule is here. It is enforced for anyone who arrives through this method
      // and for nobody else.
      if (service.seatsLeft(trackSlug) <= 0) {
        return ResponseEntity.status(409).body("no seats left");
      }
      return ResponseEntity.status(201).body("seats left: " + service.takeSeat(trackSlug));
    }
  }

  // A second entry point added six months later: an administrative bulk enrolment
  // that calls the service, because that is what services are for.
  static class BulkEnroller {
    private final EnrolmentService service;

    BulkEnroller(EnrolmentService service) {
      this.service = service;
    }

    void enrolEveryone(String trackSlug, int people) {
      for (int i = 0; i < people; i++) {
        service.takeSeat(trackSlug);
      }
    }
  }

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
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/enrolments/spring-boot-path"))
            .andReturn();
    System.out.println("http request 1: " + first.getResponse().getStatus());

    MvcResult second =
        mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/enrolments/spring-boot-path"))
            .andReturn();
    System.out.println(
        "http request 2: "
            + second.getResponse().getStatus()
            + " "
            + second.getResponse().getContentAsString());

    new BulkEnroller(service).enrolEveryone("spring-boot-path", 40);
    System.out.println("after the bulk enroller: seatsLeft=" + service.seatsLeft("spring-boot-path"));
    System.out.println("the rule the controller enforced was never applied here");
  }
}
