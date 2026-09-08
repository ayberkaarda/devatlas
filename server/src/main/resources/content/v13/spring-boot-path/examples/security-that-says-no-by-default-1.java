// Spring Boot 4.1, Spring Security 7.1. A filter chain that ends in anyRequest()
// .authenticated(), and the three answers it gives to the same three paths.
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class SecurityDefaultDenyDemo {

  @RestController
  static class Endpoints {
    @GetMapping("/api/v1/tracks")
    String publicRead() {
      return "public";
    }

    @GetMapping("/api/v1/sync/progress")
    String authenticated() {
      return "signed in";
    }

    @GetMapping("/api/v1/admin/whitelist-sources")
    String adminOnly() {
      return "administrator";
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @EnableWebSecurity
  static class SecurityConfiguration {
    @Bean
    Endpoints endpoints() {
      return new Endpoints();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      return http.csrf(csrf -> csrf.disable())
          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(
              auth ->
                  auth
                      // Published content is public by definition, and only for GET.
                      .requestMatchers(HttpMethod.GET, "/api/v1/tracks/**")
                      .permitAll()
                      .requestMatchers("/api/v1/admin/**")
                      .hasRole("ADMIN")
                      // Everything not named above, including every path added
                      // tomorrow, needs a caller.
                      .anyRequest()
                      .authenticated())
          .build();
    }
  }

  static void quietLogging() {
    ((ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
  }

  static void call(MockMvc mockMvc, String who, RequestPostProcessor caller, String path)
      throws Exception {
    var request = MockMvcRequestBuilders.get(path);
    if (caller != null) {
      request = request.with(caller);
    }
    MvcResult result = mockMvc.perform(request).andReturn();
    System.out.printf("%-13s %-34s %d%n", who, path, result.getResponse().getStatus());
  }

  public static void main(String[] args) throws Exception {
    quietLogging();

    AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    context.register(SecurityConfiguration.class);
    context.setServletContext(new MockServletContext());
    context.refresh();

    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    for (String path :
        new String[] {"/api/v1/tracks", "/api/v1/sync/progress", "/api/v1/admin/whitelist-sources"}) {
      call(mockMvc, "anonymous", null, path);
      call(mockMvc, "role USER", user("reader").roles("USER"), path);
      call(mockMvc, "role ADMIN", user("root").roles("ADMIN"), path);
    }
    context.close();
  }
}
