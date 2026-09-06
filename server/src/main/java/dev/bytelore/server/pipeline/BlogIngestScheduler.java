package dev.bytelore.server.pipeline;

import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.domain.WhitelistSource;
import dev.bytelore.server.repository.WhitelistSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fires every six hours (configurable) and runs one fetch cycle for every {@code enabled = true}
 * {@link WhitelistSource} row. Calls the exact same {@link BlogIngestPipelineService#runFetchCycle}
 * the manual trigger calls -- see that class's Javadoc for why that identity matters.
 *
 * <p>{@code actorUserId} is {@code null} for every call here: a scheduled run has no human behind
 * it, and the audit trail records that honestly.
 */
@Component
public class BlogIngestScheduler {

  private static final Logger log = LoggerFactory.getLogger(BlogIngestScheduler.class);

  private final WhitelistSourceRepository whitelistSources;
  private final BlogIngestPipelineService pipeline;

  public BlogIngestScheduler(
      WhitelistSourceRepository whitelistSources, BlogIngestPipelineService pipeline) {
    this.whitelistSources = whitelistSources;
    this.pipeline = pipeline;
  }

  @Scheduled(cron = "${bytelore.pipeline.fetch-cron:0 0 0/6 * * *}")
  public void fetchAllEnabledSources() {
    for (WhitelistSource source : whitelistSources.findByEnabledTrue()) {
      try {
        pipeline.runFetchCycle(source.getId(), null);
      } catch (ApiException e) {
        if (e.code() == ErrorCode.PIPELINE_RUN_IN_PROGRESS) {
          log.info(
              "Skipping scheduled fetch for '{}': a run is already in progress.", source.getName());
        } else {
          log.warn("Scheduled fetch failed for '{}': {}", source.getName(), e.getMessage());
        }
      } catch (Exception e) {
        log.error("Scheduled fetch failed unexpectedly for '{}'.", source.getName(), e);
      }
    }
  }
}
