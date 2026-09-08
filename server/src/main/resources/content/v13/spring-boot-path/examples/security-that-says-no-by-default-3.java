// Spring Boot 4.1, Spring Security 7.1. The chain is a list of servlet filters in a
// fixed order, and where a custom filter is inserted decides what it can see.
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class SecurityFilterOrderDemo {

  /** Stands in for a bearer-token filter: it establishes who the caller is. */
  static class TokenAuthenticationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      chain.doFilter(request, response);
    }
  }

  /**
   * Keys a per-caller budget on the authenticated principal, so it has to run after
   * authentication has happened. Placed before it, the principal is not there yet
   * and every request looks anonymous.
   */
  static class PerUserRateLimitFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      var authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication == null) {
        throw new AuthorizationDeniedException("no principal to key a budget on");
      }
      chain.doFilter(request, response);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableWebMvc
  @EnableWebSecurity
  static class SecurityConfiguration {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      return http.csrf(csrf -> csrf.disable())
          .httpBasic(basic -> basic.disable())
          .formLogin(form -> form.disable())
          .logout(logout -> logout.disable())
          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
          .addFilterBefore(new TokenAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
          .addFilterAfter(new PerUserRateLimitFilter(), TokenAuthenticationFilter.class)
          .build();
    }
  }

  static void quietLogging() {
    ((ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
        .setLevel(ch.qos.logback.classic.Level.OFF);
  }

  public static void main(String[] args) {
    quietLogging();

    try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
      context.register(SecurityConfiguration.class);
      context.setServletContext(new MockServletContext());
      context.refresh();

      SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);
      int position = 0;
      for (var filter : chain.getFilters()) {
        String name = filter.getClass().getSimpleName();
        String note =
            switch (name) {
              case "TokenAuthenticationFilter" -> "   <- establishes the principal";
              case "PerUserRateLimitFilter" -> "   <- needs the principal, so it comes after";
              case "AuthorizationFilter" -> "   <- decides, last, on what the earlier filters left";
              default -> "";
            };
        System.out.printf("%2d %s%s%n", position++, name, note);
      }
      System.out.println("AuthorizationFilter is the last one: "
          + chain.getFilters().get(chain.getFilters().size() - 1).getClass().equals(AuthorizationFilter.class));
    }
  }
}
