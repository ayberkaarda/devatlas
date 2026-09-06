package dev.bytelore.server.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Per-minute request budgets for the two anonymous endpoint families (§3.6 of the REST contract).
 *
 * <p>They are configuration values rather than constants because the right numbers depend on the
 * deployment, and the ratio between them is deliberate: a client reads a handful of manifests and
 * then downloads many packages, with up to three concurrent transfers and resume, so a limit that
 * comfortably covers manifest polling would strangle a legitimate library download.
 */
@Component
@ConfigurationProperties(prefix = "bytelore.rate-limit")
public class RateLimitProperties {

  /** Requests per minute per client IP on {@code GET /api/v1/manifest/**}. */
  private int manifestPerMinute = 60;

  /** Requests per minute per client IP on {@code GET /api/v1/content/**}. */
  private int contentPerMinute = 600;

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
}
