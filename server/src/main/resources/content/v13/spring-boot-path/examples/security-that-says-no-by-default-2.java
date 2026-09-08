// Spring Boot 4.1, Spring Security 7.1. Rules are matched in the order written and
// only the first match applies, so a broad rule written first hides the narrow one.
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class SecurityRuleOrderDemo {

  @RestController
  static class Endpoints {
    @GetMapping("/api/v1/tracks")
    String publicRead() {
      return "public";
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @EnableWebSecurity
  static class BroadRuleFirst {
    @Bean
    Endpoints endpoints() {
      return new Endpoints();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      return http.csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(
              auth ->
                  auth
                      // Matches /api/v1/tracks, so the rule below is never consulted.
                      .requestMatchers("/api/v1/**")
                      .authenticated()
                      .requestMatchers(HttpMethod.GET, "/api/v1/tracks/**")
                      .permitAll()
                      .anyRequest()
                      .denyAll())
          .build();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @EnableWebSecurity
  static class NarrowRuleFirst {
    @Bean
    Endpoints endpoints() {
      return new Endpoints();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      return http.csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(
              auth ->
                  auth.requestMatchers(HttpMethod.GET, "/api/v1/tracks/**")
                      .permitAll()
                      .requestMatchers("/api/v1/**")
                      .authenticated()
                      .anyRequest()
                      .denyAll())
          .build();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @EnableWebSecurity
  static class RuleAfterAnyRequest {
    @Bean
    Endpoints endpoints() {
      return new Endpoints();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      return http.csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(
              auth ->
                  auth.anyRequest()
                      .authenticated()
                      // Unreachable by construction, and refused rather than ignored.
                      .requestMatchers(HttpMethod.GET, "/api/v1/tracks/**")
                      .permitAll())
          .build();
    }
  }

  static void quietLogging() {
    ((ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
  }

  static void anonymousGet(String label, Class<?> configuration) {
    AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
    try {
      context.register(configuration);
      context.setServletContext(new MockServletContext());
      context.refresh();
      MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
      MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/tracks")).andReturn();
      System.out.println(label + ": anonymous GET /api/v1/tracks -> " + result.getResponse().getStatus());
    } catch (Exception failure) {
      Throwable root = failure;
      while (root.getCause() != null) {
        root = root.getCause();
      }
      System.out.println(label + ": refused to build -> " + root.getClass().getSimpleName());
      System.out.println("   " + root.getMessage());
    } finally {
      context.close();
    }
  }

  public static void main(String[] args) {
    quietLogging();
    anonymousGet("broad rule first ", BroadRuleFirst.class);
    anonymousGet("narrow rule first", NarrowRuleFirst.class);
    anonymousGet("after anyRequest ", RuleAfterAnyRequest.class);
  }
}
