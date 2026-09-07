package dev.bytelore.server.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Request budgets for the throttled endpoint families (§3.6 of the REST contract).
 *
 * <p>They are configuration values rather than constants because the right numbers depend on the
 * deployment, and the ratio between the two anonymous ones is deliberate: a client reads a handful
 * of manifests and then downloads many packages, with up to three concurrent transfers and resume,
 * so a limit that comfortably covers manifest polling would strangle a legitimate library download.
 */
@Component
@ConfigurationProperties(prefix = "bytelore.rate-limit")
public class RateLimitProperties {

  /** Requests per minute per client IP on {@code GET /api/v1/manifest/**}. */
  private int manifestPerMinute = 60;

  /** Requests per minute per client IP on {@code GET /api/v1/content/**}. */
  private int contentPerMinute = 600;

  /**
   * Requests per hour per authenticated user on {@code /api/v1/sync/progress}, applied separately
   * to each direction. Keyed by user rather than by address: this endpoint has a principal, and a
   * principal neither punishes an office for sharing an exit address nor can be shed by moving to
   * another network.
   */
  private int syncProgressPerHour = 120;

  /**
   * Sign-in attempts per 15 minutes, per email address and client address together. Both halves
   * matter: the address alone would lock out everyone behind one office gateway as soon as a single
   * colleague fat-fingered a password, and the email alone would let anyone lock a named account
   * out of its own sign-in from anywhere. Together they throttle guessing at one account from one
   * place, which is the shape the attack actually has.
   */
  private int loginPer15Minutes = 10;

  /** Account creations per hour per client address. */
  private int registerPerHour = 5;

  /** Token exchanges per hour per user; the owner of the presented refresh token. */
  private int refreshPerHour = 60;

  /** Sign-outs per hour per client address. */
  private int logoutPerHour = 60;

  /** Password changes per hour per authenticated user. */
  private int passwordChangePerHour = 5;

  public int getManifestPerMinute() {
    return manifestPerMinute;
  }

  public void setManifestPerMinute(int manifestPerMinute) {
    this.manifestPerMinute = manifestPerMinute;
  }

  public int getContentPerMinute() {
    return contentPerMinute;
  }

  public void setContentPerMinute(int contentPerMinute) {
    this.contentPerMinute = contentPerMinute;
  }

  public int getSyncProgressPerHour() {
    return syncProgressPerHour;
  }

  public void setSyncProgressPerHour(int syncProgressPerHour) {
    this.syncProgressPerHour = syncProgressPerHour;
  }

  public int getLoginPer15Minutes() {
    return loginPer15Minutes;
  }

  public void setLoginPer15Minutes(int loginPer15Minutes) {
    this.loginPer15Minutes = loginPer15Minutes;
  }

  public int getRegisterPerHour() {
    return registerPerHour;
  }

  public void setRegisterPerHour(int registerPerHour) {
    this.registerPerHour = registerPerHour;
  }

  public int getRefreshPerHour() {
    return refreshPerHour;
  }

  public void setRefreshPerHour(int refreshPerHour) {
    this.refreshPerHour = refreshPerHour;
  }

  public int getLogoutPerHour() {
    return logoutPerHour;
  }

  public void setLogoutPerHour(int logoutPerHour) {
    this.logoutPerHour = logoutPerHour;
  }

  public int getPasswordChangePerHour() {
    return passwordChangePerHour;
  }

  public void setPasswordChangePerHour(int passwordChangePerHour) {
    this.passwordChangePerHour = passwordChangePerHour;
  }
}
