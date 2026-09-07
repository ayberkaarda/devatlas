package dev.bytelore.server.config;

import dev.bytelore.server.ratelimit.FixedWindowRateLimiter;
import dev.bytelore.server.ratelimit.ProgressSyncRateLimitFilter;
import dev.bytelore.server.ratelimit.RateLimitProperties;
import dev.bytelore.server.security.ErrorResponseWriter;
import dev.bytelore.server.security.JwtAuthenticationFilter;
import dev.bytelore.server.security.RestAccessDeniedHandler;
import dev.bytelore.server.security.RestAuthenticationEntryPoint;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Authentication wiring and the endpoint authorization matrix. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

  /**
   * bcrypt at cost 12. The cost is the point of the algorithm: it is chosen to make a verification
   * slow enough that offline guessing against a stolen table is expensive, and it has to be raised
   * over the years as hardware improves.
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  /**
   * One clock, injected everywhere a timestamp is produced, so that no code path reaches for the
   * system clock directly. Token lifetimes, rotation windows and last-write-wins comparisons are
   * all time arithmetic, and time arithmetic that cannot be controlled cannot be tested.
   */
  @Bean
  Clock systemClock() {
    return Clock.systemUTC();
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationFilter jwtAuthenticationFilter,
      RestAuthenticationEntryPoint authenticationEntryPoint,
      RestAccessDeniedHandler accessDeniedHandler,
      FixedWindowRateLimiter rateLimiter,
      RateLimitProperties rateLimitProperties,
      ErrorResponseWriter errorResponseWriter)
      throws Exception {
    // Constructed here rather than declared as a bean on purpose. A Filter bean is picked up by
    // the servlet container's own registration as well, which would run it a second time and,
    // worse, run that copy ahead of authentication -- where the principal it keys on does not
    // exist yet.
    ProgressSyncRateLimitFilter progressSyncRateLimitFilter =
        new ProgressSyncRateLimitFilter(rateLimiter, rateLimitProperties, errorResponseWriter);
    return http
        // No CSRF token machinery. Authenticated calls carry a bearer token in a header, which a
        // cross-site form cannot set; the one cookie in play is the refresh token, and it is
        // SameSite=Strict, so a cross-site request never carries it either.
        .csrf(csrf -> csrf.disable())
        .cors(Customizer.withDefaults())
        // No server-side session exists to fix, follow or steal.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .authorizeHttpRequests(
            auth ->
                auth
                    // The four unauthenticated auth endpoints. Sign-out is among them by
                    // necessity: the credential it acts on is the refresh token, and a user whose
                    // access token expired while offline must still be able to sign out.
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout")
                    .permitAll()
                    // Manifest and package reads take no credentials at all. Published lesson
                    // bodies are already readable anonymously through the public read API, so a
                    // gate here would be a gate on a door standing beside an open wall -- and it
                    // would force the download engine to hold a session, which cannot be shared
                    // with the interface layer because refresh tokens are single-use.
                    .requestMatchers(HttpMethod.GET, "/api/v1/manifest/**", "/api/v1/content/**")
                    .permitAll()
                    // Published content is public by definition.
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/tracks/**",
                        "/api/v1/lessons/**",
                        "/api/v1/blog/posts/**")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    // Present only under the development profile; absent everywhere else.
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    // The trust chain for automatically sourced content is administered by
                    // administrators only.
                    .requestMatchers("/api/v1/admin/whitelist-sources/**")
                    .hasRole("ADMIN")
                    // Approving, rejecting and withdrawing published content is the one decision
                    // that stays with the highest privilege level.
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/admin/blog/posts/*/approve",
                        "/api/v1/admin/blog/posts/*/reject",
                        "/api/v1/admin/blog/posts/*/unpublish")
                    .hasRole("ADMIN")
                    .requestMatchers("/api/v1/admin/**")
                    .hasAnyRole("EDITOR", "ADMIN")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler))
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        // After authentication, because it keys its budget on the caller's user id. An
        // unauthenticated request passes through untouched and is answered by the entry point.
        .addFilterAfter(progressSyncRateLimitFilter, JwtAuthenticationFilter.class)
        .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.getAllowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "Accept-Language", "X-Request-Id"));
    configuration.setExposedHeaders(List.of("Content-Language", "Retry-After", "X-Request-Id"));
    // Credentials are involved, which is what makes a wildcard origin impossible here -- and that
    // constraint is a feature: the allowed set has to be written down and reviewed.
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
