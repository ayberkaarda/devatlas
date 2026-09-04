package dev.devatlas.server.security;

import dev.devatlas.server.auth.AccessTokenClaims;
import dev.devatlas.server.auth.JwtService;
import dev.devatlas.server.common.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the bearer token, if one is present, and populates the security context.
 *
 * <p>A bad token is <strong>not</strong> rejected here. The failure is recorded on the request and
 * the chain continues unauthenticated, for two reasons. The public read endpoints and the anonymous
 * manifest and package endpoints must keep working for a caller who happens to be holding an
 * expired token -- a token presented there is simply ignored. And when a protected endpoint is then
 * refused, the entry point can report exactly why: whether the token was expired, which the client
 * answers by refreshing, or structurally invalid, which it does not.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  /** Where a verification failure is parked for the entry point to find. */
  public static final String AUTHENTICATION_FAILURE = "devatlas.authenticationFailure";

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtService jwt;

  public JwtAuthenticationFilter(JwtService jwt) {
    this.jwt = jwt;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      try {
        AccessTokenClaims claims = jwt.verify(header.substring(BEARER_PREFIX.length()).trim());
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                claims, null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (ApiException e) {
        SecurityContextHolder.clearContext();
        request.setAttribute(AUTHENTICATION_FAILURE, e);
      }
    }
    chain.doFilter(request, response);
  }
}
