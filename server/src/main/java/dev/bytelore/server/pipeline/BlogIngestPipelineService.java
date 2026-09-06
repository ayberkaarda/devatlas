package dev.bytelore.server.pipeline;

import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.domain.PipelineStep;
import dev.bytelore.server.domain.WhitelistSource;
import dev.bytelore.server.repository.WhitelistSourceRepository;
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

  /**
   * The failed-check name reported when processing one feed item threw rather than returning an
   * outcome. It is not a member of the verification chain -- no source update was written, so there
   * is nothing to attach a chain to -- but the manual fetch response and the source's audit trail
   * both name failures by check, and an item that fell over needs a name there too.
   */
  private static final String ITEM_FAILED = "ITEM_FAILED";

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
    int deferred = 0;
    List<UUID> createdIds = new ArrayList<>();
    List<Rejection> rejections = new ArrayList<>();
    for (FeedItem item : items) {
      ItemOutcome outcome;
      try {
        outcome = itemProcessor.process(source, item, actorUserId);
      } catch (RuntimeException e) {
        // One malformed or hostile item must not end a source's cycle. Each item is processed in
        // its own transaction, so a failure here has already rolled that item's writes back and
        // nothing else; abandoning the loop would additionally skip every later item and leave the
        // fetch timestamp unset, so the next run would meet the same item first and stall in the
        // same place -- a source that quietly stops ingesting, with no record of why. The failure
        // is
        // counted as a rejection and audited against the source instead.
        outcome = ItemOutcome.rejected(new Rejection(null, ITEM_FAILED, describe(e)));
        auditor.record(
            PipelineStep.VERIFY,
            null,
            null,
            source.getId(),
            actorUserId,
            null,
            null,
            "Rejected at %s: %s".formatted(ITEM_FAILED, describe(e)));
      }
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
        case DEFERRED -> deferred++;
      }
    }

    touchLastFetchedAt(source, start);
    if (deferred > 0) {
      log.info(
          "Fetch cycle for whitelist source '{}' deferred {} item(s) to the next cycle (transient"
              + " rate limit).",
          source.getName(),
          deferred);
    }
    long durationMs = Duration.between(start, Instant.now(clock)).toMillis();
    // Deliberately created + duplicates + rejected, not items.size(): a deferred item was skipped
    // for this cycle rather than decided, so it is excluded from every count in the response, not
    // only from `rejected` (§5.7 documents this explicitly).
    return new FetchCycleResult(
        source.getId(),
        created + duplicates + rejected,
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

  /**
   * A one-line description of a failure, for the audit trail and the fetch response. The exception
   * type is included because a message alone rarely says whether the cause was the item's content
   * or the infrastructure underneath it.
   */
  private static String describe(RuntimeException e) {
    String message = e.getMessage();
    return message == null || message.isBlank()
        ? e.getClass().getSimpleName()
        : "%s: %s".formatted(e.getClass().getSimpleName(), message);
  }

  private void touchLastFetchedAt(WhitelistSource source, Instant at) {
    source.setLastFetchedAt(at);
    whitelistSources.save(source);
  }
}
