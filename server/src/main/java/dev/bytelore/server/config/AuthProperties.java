package dev.bytelore.server.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable parameters of the authentication layer.
 *
 * <p>Every lifetime here is configuration rather than a compile-time constant, because the right
 * value depends on the deployment and because changing one must not require a rebuild.
 */
@ConfigurationProperties(prefix = "bytelore.auth")
public class AuthProperties {

  /**
   * HMAC key for the access token signature, supplied through the environment. At least 32 bytes:
   * HS256 with a shorter key is not a weaker signature, it is a signature an attacker can search
   * for.
   */
  private String jwtSecret;

  /**
   * Access token lifetime. Short enough that a leaked token has little value, long enough that a
   * normal reading session makes few refresh calls.
   */
  private Duration accessTtl = Duration.ofMinutes(15);

  /**
   * Refresh token lifetime, applied per token rather than per family: every rotation mints a
   * successor with a full fresh lifetime instead of inheriting the remainder of its predecessor's.
   *
   * <p>Without that rule an active daily user would be signed out on a fixed schedule for no reason
   * -- the very outcome a long window was chosen to avoid -- while an idle user would be signed out
   * on exactly the same one. Regular use has to extend a session; only absence may end it.
   */
  private Duration refreshTtl = Duration.ofDays(60);

  /**
   * How long after a rotation a retry of the same token is treated as a lost response rather than a
   * replay.
   *
   * <p>This is a deliberate, bounded weakening of reuse detection -- a short window in which a
   * stolen token can be exchanged once -- accepted so that a dropped connection or a laptop
   * suspended mid-request does not sign out an innocent user.
   */
  private Duration refreshGrace = Duration.ofSeconds(30);

  /** Tolerance for clock drift when verifying an access token's time claims. */
  private Duration clockSkew = Duration.ofSeconds(60);

  /** Name of the cookie carrying the refresh token in the browser delivery mode. */
  private String cookieName = "bytelore_refresh";

  /**
   * Cookie path. Deliberately the whole auth group rather than just the refresh endpoint, so that
   * sign-out also receives the cookie -- logout has to be able to revoke the token it is asked to
   * revoke.
   */
  private String cookiePath = "/api/v1/auth";

  /**
   * Whether the refresh cookie is marked {@code Secure}. True everywhere a browser talks to this
   * API over HTTPS, which is everywhere that is not a developer's laptop.
   */
  private boolean cookieSecure = true;

  /**
   * Origins allowed to make credentialed browser requests. Never a wildcard: credentials are
   * involved, which makes a wildcard impossible anyway, and an explicit list is the only form that
   * can be reviewed.
   *
   * <p>The list has to include the desktop webview origins as well. The same application runs
   * inside that webview and issues its calls through the browser stack, so its requests are
   * preflighted like any other; omitting them produces a failure that looks like nothing at all --
   * every desktop call fails at the preflight with no body to inspect, while identical code works
   * in a browser.
   */
  private List<String> allowedOrigins = new ArrayList<>();

  public String getJwtSecret() {
    return jwtSecret;
  }

  public void setJwtSecret(String jwtSecret) {
    this.jwtSecret = jwtSecret;
  }

  public Duration getAccessTtl() {
    return accessTtl;
  }

  public void setAccessTtl(Duration accessTtl) {
    this.accessTtl = accessTtl;
  }

  public Duration getRefreshTtl() {
    return refreshTtl;
  }

  public void setRefreshTtl(Duration refreshTtl) {
    this.refreshTtl = refreshTtl;
  }

  public Duration getRefreshGrace() {
    return refreshGrace;
  }

  public void setRefreshGrace(Duration refreshGrace) {
    this.refreshGrace = refreshGrace;
  }

  public Duration getClockSkew() {
    return clockSkew;
  }

  public void setClockSkew(Duration clockSkew) {
    this.clockSkew = clockSkew;
  }

  public String getCookieName() {
    return cookieName;
  }

  public void setCookieName(String cookieName) {
    this.cookieName = cookieName;
  }

  public String getCookiePath() {
    return cookiePath;
  }

  public void setCookiePath(String cookiePath) {
    this.cookiePath = cookiePath;
  }

  public boolean isCookieSecure() {
    return cookieSecure;
  }

  public void setCookieSecure(boolean cookieSecure) {
    this.cookieSecure = cookieSecure;
  }

  public List<String> getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(List<String> allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }
}
