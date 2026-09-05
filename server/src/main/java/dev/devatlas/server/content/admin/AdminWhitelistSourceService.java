package dev.devatlas.server.content.admin;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.common.PageQuery;
import dev.devatlas.server.common.PageResponse;
import dev.devatlas.server.common.RateLimitedException;
import dev.devatlas.server.common.UuidV7;
import dev.devatlas.server.content.admin.dto.CreateWhitelistSourceRequest;
import dev.devatlas.server.content.admin.dto.RejectionItem;
import dev.devatlas.server.content.admin.dto.UpdateWhitelistSourceRequest;
import dev.devatlas.server.content.admin.dto.WhitelistSourceFetchResponse;
import dev.devatlas.server.content.admin.dto.WhitelistSourceResponse;
import dev.devatlas.server.domain.WhitelistSource;
import dev.devatlas.server.pipeline.BlogIngestPipelineService;
import dev.devatlas.server.pipeline.FetchCycleResult;
import dev.devatlas.server.pipeline.PipelineProperties;
import dev.devatlas.server.pipeline.Rejection;
import dev.devatlas.server.ratelimit.FixedWindowRateLimiter;
import dev.devatlas.server.repository.SourceUpdateRepository;
import dev.devatlas.server.repository.WhitelistSourceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administration of {@link WhitelistSource} rows, and the manual fetch trigger (§5.7).
 *
 * <p>The trust chain for automatically sourced content starts here: {@code ADMIN}-only for every
 * verb, enforced in {@code SecurityConfig}. {@link #manualFetch} calls the exact same {@link
 * BlogIngestPipelineService#runFetchCycle} the scheduler calls, with no argument that alters
 * behaviour beyond attributing the run to the calling administrator (§5.7: "It is not a second code
 * path").
 */
@Service
public class AdminWhitelistSourceService {

  private static final Map<String, String> SORT_FIELDS =
      Map.of(
          "name", "name",
          "created_at", "createdAt",
          "updated_at", "updatedAt",
          "last_fetched_at", "lastFetchedAt");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "name");
  private static final Duration RATE_LIMIT_WINDOW = Duration.ofHours(1);

  private final WhitelistSourceRepository whitelistSources;
  private final SourceUpdateRepository sourceUpdates;
  private final BlogIngestPipelineService pipeline;
  private final FixedWindowRateLimiter rateLimiter;
  private final PipelineProperties properties;
  private final Clock clock;

  public AdminWhitelistSourceService(
      WhitelistSourceRepository whitelistSources,
      SourceUpdateRepository sourceUpdates,
      BlogIngestPipelineService pipeline,
      FixedWindowRateLimiter rateLimiter,
      PipelineProperties properties,
      Clock clock) {
    this.whitelistSources = whitelistSources;
    this.sourceUpdates = sourceUpdates;
    this.pipeline = pipeline;
    this.rateLimiter = rateLimiter;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public WhitelistSourceResponse create(CreateWhitelistSourceRequest request) {
    if (whitelistSources.existsByName(request.name())) {
      throw new ApiException(
          ErrorCode.SLUG_ALREADY_EXISTS,
          "A whitelist source already exists with name '%s'.".formatted(request.name()));
    }
    requireHttps(request.feedUrl());
    requireHttps(request.verifyUrlPattern());
    requireExactlyOnePlaceholder(request.verifyUrlPattern());

    Instant now = now();
    WhitelistSource source = new WhitelistSource();
    source.setId(UuidV7.randomUuid());
    source.setName(request.name());
    source.setFeedUrl(request.feedUrl());
    source.setVerifyUrlPattern(request.verifyUrlPattern());
    source.setEnabled(request.enabled() == null || request.enabled());
    source.setCreatedAt(now);
    source.setUpdatedAt(now);
    WhitelistSource saved = whitelistSources.save(source);
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public PageResponse<WhitelistSourceResponse> list(Integer page, Integer size, List<String> sort) {
    Pageable pageable = PageQuery.resolve(page, size, sort, SORT_FIELDS, DEFAULT_SORT);
    Page<WhitelistSource> result = whitelistSources.findAll(pageable);
    List<WhitelistSourceResponse> items =
        result.getContent().stream().map(this::toResponse).toList();
    return PageResponse.of(items, result.getNumber(), result.getSize(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public WhitelistSourceResponse get(UUID id) {
    return toResponse(requireSource(id));
  }

  @Transactional
  public WhitelistSourceResponse update(UUID id, UpdateWhitelistSourceRequest request) {
    WhitelistSource source = requireSource(id);
    requireVersion(source.getVersion(), request.version());

    boolean changed = false;
    if (request.name() != null && !request.name().equals(source.getName())) {
      if (whitelistSources.existsByNameAndIdNot(request.name(), id)) {
        throw new ApiException(
            ErrorCode.SLUG_ALREADY_EXISTS,
            "A whitelist source already exists with name '%s'.".formatted(request.name()));
      }
      source.setName(request.name());
      changed = true;
    }
    if (request.feedUrl() != null) {
      requireHttps(request.feedUrl());
      source.setFeedUrl(request.feedUrl());
      changed = true;
    }
    if (request.verifyUrlPattern() != null) {
      requireHttps(request.verifyUrlPattern());
      requireExactlyOnePlaceholder(request.verifyUrlPattern());
      source.setVerifyUrlPattern(request.verifyUrlPattern());
      changed = true;
    }
    if (request.enabled() != null && request.enabled() != source.isEnabled()) {
      source.setEnabled(request.enabled());
      changed = true;
    }

    if (changed) {
      source.setUpdatedAt(now());
    }
    WhitelistSource saved = whitelistSources.save(source);
    return toResponse(saved);
  }

  @Transactional
  public void delete(UUID id) {
    WhitelistSource source = requireSource(id);
    if (sourceUpdates.existsByWhitelistSourceId(id)) {
      throw new ApiException(
          ErrorCode.PARENT_NOT_EMPTY,
          "This source has fetched updates on record; disable it instead of deleting it, so"
              + " provenance for any post it produced stays reconstructible.");
    }
    whitelistSources.delete(source);
  }

  /**
   * Runs the ingest cycle for one source immediately (§5.7). Rate-limited to {@code
   * manualFetchPerHour} requests per hour per source; a held lock (the scheduler or another manual
   * run already in flight for this source) answers {@code 409 PIPELINE_RUN_IN_PROGRESS} rather than
   * queueing or blocking, because {@link BlogIngestPipelineService#runFetchCycle} raises that
   * itself.
   */
  public WhitelistSourceFetchResponse manualFetch(UUID id, UUID actorUserId) {
    WhitelistSource source = requireSource(id);

    FixedWindowRateLimiter.Decision decision =
        rateLimiter.record(
            "whitelist-fetch:" + id, properties.getManualFetchPerHour(), RATE_LIMIT_WINDOW);
    if (!decision.allowed()) {
      throw new RateLimitedException(
          "Too many manual fetch requests for this source; retry later.",
          decision.retryAfterSeconds());
    }

    FetchCycleResult result = pipeline.runFetchCycle(source.getId(), actorUserId);
    return toResponse(result);
  }

  private WhitelistSourceFetchResponse toResponse(FetchCycleResult result) {
    List<RejectionItem> rejections =
        result.rejections().stream().map(this::toRejectionItem).toList();
    return new WhitelistSourceFetchResponse(
        result.whitelistSourceId(),
        result.fetched(),
        result.created(),
        result.duplicates(),
        result.rejected(),
        result.createdSourceUpdateIds(),
        rejections,
        result.durationMs());
  }

  private RejectionItem toRejectionItem(Rejection rejection) {
    return new RejectionItem(
        rejection.versionString(), rejection.failedCheck(), rejection.detail());
  }

  private static void requireHttps(String url) {
    if (url == null || !url.startsWith("https://")) {
      throw new ApiException(
          ErrorCode.INSECURE_SOURCE_URL, "'%s' must be an absolute https:// URL.".formatted(url));
    }
  }

  private static void requireExactlyOnePlaceholder(String pattern) {
    int first = pattern.indexOf("{version}");
    int last = pattern.lastIndexOf("{version}");
    if (first < 0 || first != last) {
      throw new ApiException(
          ErrorCode.INVALID_VERIFY_URL_PATTERN,
          "'verify_url_pattern' must contain exactly one '{version}' placeholder.");
    }
  }

  private WhitelistSourceResponse toResponse(WhitelistSource source) {
    return new WhitelistSourceResponse(
        source.getId(),
        source.getName(),
        source.getFeedUrl(),
        source.getVerifyUrlPattern(),
        source.isEnabled(),
        source.getLastFetchedAt(),
        source.getCreatedAt(),
        source.getUpdatedAt(),
        source.getVersion());
  }

  private WhitelistSource requireSource(UUID id) {
    return whitelistSources
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.WHITELIST_SOURCE_NOT_FOUND,
                    "No whitelist source exists with id '%s'.".formatted(id)));
  }

  private static void requireVersion(long actual, long expected) {
    if (actual != expected) {
      throw new ApiException(
          ErrorCode.VERSION_CONFLICT,
          "The resource was modified concurrently; re-read it and retry.");
    }
  }

  private Instant now() {
    return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
  }
}
