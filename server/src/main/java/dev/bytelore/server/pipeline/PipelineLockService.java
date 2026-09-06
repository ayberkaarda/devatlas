package dev.bytelore.server.pipeline;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.stereotype.Component;

/**
 * The single ShedLock lock, keyed per whitelist source, that both the scheduler and the manual
 * fetch trigger acquire before running a fetch cycle for that source (§5.7: "the same ShedLock lock
 * name" -- "a manual run and a scheduled run can never process the same source concurrently").
 *
 * <p>Deliberately programmatic rather than the declarative {@code @SchedulerLock} annotation:
 * {@link BlogIngestPipelineService#runFetchCycle} needs to know, synchronously, whether the lock
 * was acquired, so that a manual trigger can answer {@code 409 PIPELINE_RUN_IN_PROGRESS}
 * immediately rather than silently skipping the run the way a missed scheduled tick would.
 */
@Component
public class PipelineLockService {

  private final LockProvider lockProvider;
  private final PipelineProperties properties;
  private final Clock clock;

  public PipelineLockService(
      LockProvider lockProvider, PipelineProperties properties, Clock clock) {
    this.lockProvider = lockProvider;
    this.properties = properties;
    this.clock = clock;
  }

  public Optional<SimpleLock> tryLock(UUID whitelistSourceId) {
    Instant now = Instant.now(clock);
    LockConfiguration configuration =
        new LockConfiguration(
            now,
            lockName(whitelistSourceId),
            properties.getLockAtMostFor(),
            properties.getLockAtLeastFor());
    return lockProvider.lock(configuration);
  }

  public static String lockName(UUID whitelistSourceId) {
    return "blog-pipeline-fetch-" + whitelistSourceId;
  }
}
