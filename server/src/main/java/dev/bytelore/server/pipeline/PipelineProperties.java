package dev.bytelore.server.pipeline;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tunables for the blog ingest pipeline.
 *
 * <p>Configuration values rather than constants for the same reason every other timing knob in this
 * server is: the right numbers depend on the deployment and on what is being tested.
 */
@Component
@ConfigurationProperties(prefix = "bytelore.pipeline")
public class PipelineProperties {

  private static final Logger log = LoggerFactory.getLogger(PipelineProperties.class);

  /** Six hours, expressed as a Spring cron expression: second minute hour day month weekday. */
  private String fetchCron = "0 0 0/6 * * *";

  private Duration httpConnectTimeout = Duration.ofSeconds(5);
  private Duration httpReadTimeout = Duration.ofSeconds(10);

  /**
   * The minimum normalized length a fetched item's raw content must have to pass the {@code
   * CONTENT_SANITY} check. Guards against a feed item that is technically well-formed XML but
   * carries nothing worth drafting a post from.
   */
  private int minContentLength = 40;

  /** Requests per hour per source on {@code POST /admin/whitelist-sources/{id}/fetch} (§3.6). */
  private int manualFetchPerHour = 6;

  /** ShedLock {@code lockAtMostFor}: the ceiling on how long one fetch cycle may hold its lock. */
  private Duration lockAtMostFor = Duration.ofMinutes(10);

  /**
   * ShedLock {@code lockAtLeastFor}: the floor, so a very fast run cannot be immediately re-run.
   */
  private Duration lockAtLeastFor = Duration.ofSeconds(1);

  /**
   * A GitHub personal access token, applied only to a request whose host is exactly {@code
   * api.github.com} (see {@code PipelineHttpClient}). Anonymous GitHub API calls are capped at
   * 60/hour; a token raises that ceiling to 5000/hour, which most of the whitelisted sources'
   * {@code verify_url_pattern} rows need to avoid tripping the anonymous ceiling within one
   * scheduled sweep across every enabled source.
   *
   * <p>Empty by default, and CI never supplies one: a missing token is not a startup failure, only
   * a single warning (see {@link #warnIfGithubTokenMissing}) and a lower rate ceiling. Whatever the
   * value is, it is used exclusively to build an HTTP request header -- it is never interpolated
   * into a log message, an audit reason or a response body anywhere in the pipeline.
   */
  private String githubToken = "";

  /**
   * The {@code ITEM_RECENT} check's cutoff: a feed item older than this, by its own {@code
   * published}/{@code updated} timestamp, is rejected before the pipeline ever spends a network
   * request confirming its version. Two weeks by default -- a blog's value is in its currency, and
   * a release from years ago is not news on the first run against a freshly added source either.
   */
  private Duration maxItemAge = Duration.ofDays(14);

  /**
   * Logs one warning at startup if no GitHub token is configured, and does nothing otherwise. Fires
   * exactly once because {@link PipelineProperties} is a Spring singleton and {@link PostConstruct}
   * runs once per bean instance -- no separate "already warned" flag is needed.
   */
  @PostConstruct
  void warnIfGithubTokenMissing() {
    if (githubToken == null || githubToken.isBlank()) {
      log.warn(
          "bytelore.pipeline.github-token is not set; requests to api.github.com will use the"
              + " anonymous rate limit (60/hour) instead of the authenticated one (5000/hour).");
    }
  }

  public String getFetchCron() {
    return fetchCron;
  }

  public void setFetchCron(String fetchCron) {
    this.fetchCron = fetchCron;
  }

  public Duration getHttpConnectTimeout() {
    return httpConnectTimeout;
  }

  public void setHttpConnectTimeout(Duration httpConnectTimeout) {
    this.httpConnectTimeout = httpConnectTimeout;
  }

  public Duration getHttpReadTimeout() {
    return httpReadTimeout;
  }

  public void setHttpReadTimeout(Duration httpReadTimeout) {
    this.httpReadTimeout = httpReadTimeout;
  }

  public int getMinContentLength() {
    return minContentLength;
  }

  public void setMinContentLength(int minContentLength) {
    this.minContentLength = minContentLength;
  }

  public int getManualFetchPerHour() {
    return manualFetchPerHour;
  }

  public void setManualFetchPerHour(int manualFetchPerHour) {
    this.manualFetchPerHour = manualFetchPerHour;
  }

  public Duration getLockAtMostFor() {
    return lockAtMostFor;
  }

  public void setLockAtMostFor(Duration lockAtMostFor) {
    this.lockAtMostFor = lockAtMostFor;
  }

  public Duration getLockAtLeastFor() {
    return lockAtLeastFor;
  }

  public void setLockAtLeastFor(Duration lockAtLeastFor) {
    this.lockAtLeastFor = lockAtLeastFor;
  }

  public String getGithubToken() {
    return githubToken;
  }

  public void setGithubToken(String githubToken) {
    this.githubToken = githubToken;
  }

  public Duration getMaxItemAge() {
    return maxItemAge;
  }

  public void setMaxItemAge(Duration maxItemAge) {
    this.maxItemAge = maxItemAge;
  }
}
