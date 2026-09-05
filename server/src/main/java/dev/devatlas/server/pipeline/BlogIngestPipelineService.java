package dev.devatlas.server.pipeline;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.domain.PipelineStep;
import dev.devatlas.server.domain.WhitelistSource;
import dev.devatlas.server.repository.WhitelistSourceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The one fetch cycle implementation the whole pipeline has. Both {@link BlogIngestScheduler} and
 * {@code AdminWhitelistSourceService#manualFetch} call {@link #runFetchCycle} -- the identical
 * method, under the identical ShedLock lock name -- so a manual run can never skip, reorder or
 * weaken a step of the verification chain and can never race a scheduled run against the same
 * source (§5.7).
 *
 * <p>There is no parameter here that alters what the cycle does. {@code actorUserId} changes only
 * who an audit entry is attributed to ({@code null} for the scheduler, the calling administrator
 * for a manual trigger) -- it is data recorded alongside a step, never a flag a caller can use to
 * change which steps run.
 */
@Service
public class BlogIngestPipelineService {

  private static final Logger log = LoggerFactory.getLogger(BlogIngestPipelineService.class);

  private final WhitelistSourceRepository whitelistSources;
  private final PipelineLockService lockService;
  private final PipelineHttpClient httpClient;
  private final PipelineItemProcessor itemProcessor;
  private final PipelineAuditor auditor;
  private final Clock clock;

  public BlogIngestPipelineService(
      WhitelistSourceRepository whitelistSources,
      PipelineLockService lockService,
      PipelineHttpClient httpClient,
      PipelineItemProcessor itemProcessor,
      PipelineAuditor auditor,
      Clock clock) {
    this.whitelistSources = whitelistSources;
    this.lockService = lockService;
    this.httpClient = httpClient;
    this.itemProcessor = itemProcessor;
    this.auditor = auditor;
    this.clock = clock;
  }

  public FetchCycleResult runFetchCycle(UUID whitelistSourceId, UUID actorUserId) {
    Instant start = Instant.now(clock);
    WhitelistSource source =
        whitelistSources
            .findById(whitelistSourceId)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.WHITELIST_SOURCE_NOT_FOUND,
                        "No whitelist source exists with id '%s'.".formatted(whitelistSourceId)));

    Optional<SimpleLock> lock = lockService.tryLock(whitelistSourceId);
    if (lock.isEmpty()) {
      throw new ApiException(
          ErrorCode.PIPELINE_RUN_IN_PROGRESS,
          "A fetch is already running for whitelist source '%s'.".formatted(whitelistSourceId));
    }
    try {
      return doRun(source, actorUserId, start);
    } finally {
      lock.get().unlock();
    }
  }

  private FetchCycleResult doRun(WhitelistSource source, UUID actorUserId, Instant start) {
    if (!source.isEnabled()) {
      // Fetch happens only for enabled=true rows -- not by configuration, not by parameter, and
      // not even when an administrator names a disabled source explicitly.
      auditor.record(
          PipelineStep.FETCH,
          null,
          null,
          source.getId(),
          actorUserId,
          null,
          null,
          "Source is disabled; fetch skipped.");
      return new FetchCycleResult(source.getId(), 0, 0, 0, 0, List.of(), List.of(), 0);
    }

    List<FeedItem> items = fetchItems(source, actorUserId);

    int created = 0;
    int duplicates = 0;
    int rejected = 0;
    List<UUID> createdIds = new ArrayList<>();
    List<Rejection> rejections = new ArrayList<>();
    for (FeedItem item : items) {
      ItemOutcome outcome = itemProcessor.process(source, item, actorUserId);
      switch (outcome.kind()) {
        case CREATED -> {
          created++;
          createdIds.add(outcome.sourceUpdateId());
        }
        case DUPLICATE -> duplicates++;
        case REJECTED -> {
          rejected++;
          rejections.add(outcome.rejection());
        }
      }
    }

    touchLastFetchedAt(source, start);
    long durationMs = Duration.between(start, Instant.now(clock)).toMillis();
    return new FetchCycleResult(
        source.getId(),
        items.size(),
        created,
        duplicates,
        rejected,
        createdIds,
        rejections,
        durationMs);
  }

  private List<FeedItem> fetchItems(WhitelistSource source, UUID actorUserId) {
    try {
      PipelineHttpClient.HttpFetchResult response = httpClient.get(source.getFeedUrl());
      if (response.statusCode() != 200) {
        auditor.record(
            PipelineStep.FETCH,
            null,
            null,
            source.getId(),
            actorUserId,
            null,
            null,
            "Feed request to '%s' returned HTTP %d."
                .formatted(source.getFeedUrl(), response.statusCode()));
        return List.of();
      }
      List<FeedItem> items = FeedParser.parse(response.body());
      auditor.record(
          PipelineStep.FETCH,
          null,
          null,
          source.getId(),
          actorUserId,
          null,
          null,
          "Fetched %d item(s) from '%s'.".formatted(items.size(), source.getName()));
      return items;
    } catch (Exception e) {
      log.warn("Feed fetch failed for whitelist source '{}': {}", source.getId(), e.getMessage());
      auditor.record(
          PipelineStep.FETCH,
          null,
          null,
          source.getId(),
          actorUserId,
          null,
          null,
          "Feed fetch failed: %s".formatted(e.getMessage()));
      return List.of();
    }
  }

  private void touchLastFetchedAt(WhitelistSource source, Instant at) {
    source.setLastFetchedAt(at);
    whitelistSources.save(source);
  }
}
