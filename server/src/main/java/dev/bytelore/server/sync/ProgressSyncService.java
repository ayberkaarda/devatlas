package dev.bytelore.server.sync;

import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.common.PageQuery;
import dev.bytelore.server.common.PageResponse;
import dev.bytelore.server.domain.UserProgress;
import dev.bytelore.server.domain.UserProgressId;
import dev.bytelore.server.repository.LessonRepository;
import dev.bytelore.server.repository.UserProgressRepository;
import dev.bytelore.server.sync.dto.ProgressPullItem;
import dev.bytelore.server.sync.dto.ProgressPullResponse;
import dev.bytelore.server.sync.dto.ProgressSyncItem;
import dev.bytelore.server.sync.dto.ProgressSyncRequest;
import dev.bytelore.server.sync.dto.ProgressSyncResponse;
import dev.bytelore.server.sync.dto.ProgressSyncResultItem;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Both directions of progress sync (§5.8 of the REST contract), always scoped to one caller. */
@Service
public class ProgressSyncService {

  /**
   * The documented batch ceiling. Enforced here rather than as a {@code @Size(max = 500)} on the
   * request DTO because an oversized batch has its own code and status -- {@code
   * SYNC_BATCH_TOO_LARGE}, 413 -- and Bean Validation can only report {@code VALIDATION_FAILED},
   * 400.
   */
  static final int MAX_BATCH_ITEMS = 500;

  private static final Map<String, String> SORT_FIELDS = Map.of("updated_at", "updatedAt");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "updatedAt");

  /**
   * Appended to whatever sort resolves, because {@code updated_at} alone does not order the rows
   * uniquely: a single push batch stamps every row it writes with one server instant, so several
   * hundred rows can share a value. Paging an ambiguous order is how a row gets served twice on one
   * page boundary and skipped at the next, and a sync client that loses a row that way has no way
   * of noticing.
   */
  private static final Sort PAGE_TIE_BREAKER = Sort.by(Sort.Direction.ASC, "id.lessonId");

  private final UserProgressRepository progress;
  private final LessonRepository lessons;
  private final Clock clock;

  public ProgressSyncService(
      UserProgressRepository progress, LessonRepository lessons, Clock clock) {
    this.progress = progress;
    this.lessons = lessons;
    this.clock = clock;
  }

  /**
   * Uploads a batch of local completion state (§5.8.1).
   *
   * <p>One transaction for the whole batch, and one server instant for the whole batch. The second
   * matters as much as the first: the clock clamp compares against "now", and a "now" that advances
   * between item 1 and item 500 would make an item's fate depend on where in the list it happened
   * to sit.
   *
   * @param userId the authenticated caller; the payload has no say in whose rows are written
   */
  @Transactional
  public ProgressSyncResponse push(UUID userId, ProgressSyncRequest request) {
    List<ProgressSyncItem> items = request.items();
    if (items.size() > MAX_BATCH_ITEMS) {
      throw new ApiException(
          ErrorCode.SYNC_BATCH_TOO_LARGE,
          "A sync batch carries at most %d items; this one carried %d."
              .formatted(MAX_BATCH_ITEMS, items.size()));
    }
    Set<UUID> lessonIds = distinctLessonIds(items);

    Instant serverTime = now();
    Set<UUID> liveLessonIds = Set.copyOf(lessons.findLiveIds(lessonIds));
    Map<UUID, UserProgress> stored = storedRows(userId, lessonIds);

    List<ProgressSyncResultItem> results = new ArrayList<>(items.size());
    List<UserProgress> inserted = new ArrayList<>();
    int applied = 0;
    int stale = 0;
    int rejected = 0;
    int clamped = 0;

    for (ProgressSyncItem item : items) {
      // Rule 1 first, unconditionally: the clamp is a statement about the device's clock, and it
      // is true whether or not the item goes on to be stored.
      ProgressReconciler.Clamped times =
          ProgressReconciler.clamp(item.completedAt(), item.clientUpdatedAt(), serverTime);
      if (times.clamped()) {
        clamped++;
      }

      if (!liveLessonIds.contains(item.lessonId())) {
        rejected++;
        results.add(
            new ProgressSyncResultItem(
                item.lessonId(),
                ProgressSyncStatus.REJECTED,
                ErrorCode.LESSON_NOT_FOUND,
                times.clamped(),
                null));
        continue;
      }

      UserProgress row = stored.get(item.lessonId());
      if (row == null) {
        inserted.add(newRow(userId, item.lessonId(), times, serverTime));
        applied++;
        results.add(applied(item.lessonId(), times));
      } else if (ProgressReconciler.wins(times.clientUpdatedAt(), row.getClientUpdatedAt())) {
        // Loaded inside this transaction and therefore managed: the write happens at flush, with
        // no second read.
        row.setCompletedAt(times.completedAt());
        row.setClientUpdatedAt(times.clientUpdatedAt());
        row.setUpdatedAt(serverTime);
        applied++;
        results.add(applied(item.lessonId(), times));
      } else {
        stale++;
        results.add(
            new ProgressSyncResultItem(
                item.lessonId(),
                ProgressSyncStatus.STALE,
                null,
                times.clamped(),
                // The value that won, so a client learns it without a second request.
                row.getClientUpdatedAt()));
      }
    }

    progress.saveAll(inserted);
    return new ProgressSyncResponse(serverTime, applied, stale, rejected, clamped, results);
  }

  /**
   * Reads back the caller's stored progress (§5.8.2), for a fresh installation or a device that was
   * offline while another one made progress.
   *
   * <p>Rows are returned whether or not the lesson they refer to is still live. A completion is a
   * fact about a person's history and an editorial decision to remove a lesson does not unmake it;
   * the pull direction reports what is stored, and it is the push direction that refuses to write
   * new state against a lesson that no longer resolves.
   *
   * @param since exclusive lower bound on the server's own {@code updated_at}, or null for
   *     everything
   */
  @Transactional(readOnly = true)
  public ProgressPullResponse pull(
      UUID userId, Integer page, Integer size, List<String> sort, String since) {
    Pageable pageable =
        withTieBreaker(PageQuery.resolve(page, size, sort, SORT_FIELDS, DEFAULT_SORT));
    Instant lowerBound = parseSince(since);

    Page<UserProgress> result =
        lowerBound == null
            ? progress.findByIdUserId(userId, pageable)
            : progress.findByIdUserIdAndUpdatedAtGreaterThan(userId, lowerBound, pageable);

    List<ProgressPullItem> items =
        result.getContent().stream().map(ProgressSyncService::toPullItem).toList();
    return ProgressPullResponse.of(
        PageResponse.of(items, result.getNumber(), result.getSize(), result.getTotalElements()),
        now());
  }

  private Instant now() {
    // Truncated to the precision this API serializes at, so a value a client reads back is exactly
    // the value the next comparison against it will use.
    return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
  }

  private static Set<UUID> distinctLessonIds(List<ProgressSyncItem> items) {
    Set<UUID> lessonIds = LinkedHashSet.newLinkedHashSet(items.size());
    for (ProgressSyncItem item : items) {
      if (!lessonIds.add(item.lessonId())) {
        // Refused rather than resolved by arrival order. Two entries for one lesson in one batch
        // means the client's own queue is inconsistent, and picking a winner here would hide that
        // from the only party able to fix it.
        throw new ApiException(
            ErrorCode.DUPLICATE_ITEM_IN_BATCH,
            "Lesson '%s' appears more than once in this batch.".formatted(item.lessonId()));
      }
    }
    return lessonIds;
  }

  private Map<UUID, UserProgress> storedRows(UUID userId, Set<UUID> lessonIds) {
    List<UserProgress> rows = progress.findByIdUserIdAndIdLessonIdIn(userId, lessonIds);
    Map<UUID, UserProgress> byLesson = HashMap.newHashMap(rows.size());
    for (UserProgress row : rows) {
      byLesson.put(row.getId().getLessonId(), row);
    }
    return byLesson;
  }

  private static UserProgress newRow(
      UUID userId, UUID lessonId, ProgressReconciler.Clamped times, Instant serverTime) {
    UserProgress row = new UserProgress();
    row.setId(new UserProgressId(userId, lessonId));
    row.setCompletedAt(times.completedAt());
    row.setClientUpdatedAt(times.clientUpdatedAt());
    row.setUpdatedAt(serverTime);
    return row;
  }

  private static ProgressSyncResultItem applied(UUID lessonId, ProgressReconciler.Clamped times) {
    return new ProgressSyncResultItem(
        lessonId, ProgressSyncStatus.APPLIED, null, times.clamped(), times.clientUpdatedAt());
  }

  private static ProgressPullItem toPullItem(UserProgress row) {
    return new ProgressPullItem(
        row.getId().getLessonId(),
        row.getCompletedAt(),
        row.getClientUpdatedAt(),
        row.getUpdatedAt());
  }

  private static Pageable withTieBreaker(Pageable pageable) {
    return PageRequest.of(
        pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(PAGE_TIE_BREAKER));
  }

  /**
   * Parses the {@code since} bound with the same rule the request bodies use: an explicit UTC
   * offset is required, and a naive local timestamp is refused rather than guessed at. Guessing the
   * zone is what puts a cursor hours ahead on one machine and hours behind on another, and for a
   * paging bound that means silently skipped rows.
   */
  private static Instant parseSince(String since) {
    if (since == null || since.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(since.trim()).toInstant().truncatedTo(ChronoUnit.MILLIS);
    } catch (DateTimeParseException e) {
      throw new ApiException(
          ErrorCode.INVALID_PARAMETER,
          "'since' must be an ISO-8601 timestamp with an explicit UTC offset.",
          e);
    }
  }
}
