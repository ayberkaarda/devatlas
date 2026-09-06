package dev.bytelore.server.pipeline;

import java.time.Duration;
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
}
