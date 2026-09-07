package dev.bytelore.server.ratelimit;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A fixed-window counter kept in PostgreSQL.
 *
 * <p>The whole decision is one statement. An upsert that either increments the current window's
 * count or resets it because the window has rolled takes a row lock for its duration, so two
 * concurrent requests for the same bucket serialize against each other and neither can read a count
 * the other is about to write. A read-then-write pair would be the same logic with a race in the
 * middle, and the race only shows up under exactly the load a rate limiter exists for.
 */
@Component
public class FixedWindowRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(FixedWindowRateLimiter.class);

  private static final String UPSERT =
      """
      INSERT INTO rate_limit_counters (bucket_key, window_start, hits)
      VALUES (?, ?, 1)
      ON CONFLICT (bucket_key) DO UPDATE
         SET hits = CASE WHEN rate_limit_counters.window_start = excluded.window_start
                         THEN rate_limit_counters.hits + 1
                         ELSE 1 END,
             window_start = excluded.window_start
      RETURNING hits
      """;

  private final JdbcTemplate jdbc;
  private final Clock clock;

  public FixedWindowRateLimiter(JdbcTemplate jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  /**
   * Records one request against {@code bucketKey} and reports whether it is within budget.
   *
   * <p>A database failure allows the request. Throttling is abuse control, not access control: if
   * the counter table cannot be reached, the request behind it is about to fail on its own reads
   * anyway, and turning an infrastructure fault into a wall of 429s would mislead every client into
   * backing off from a server that is not actually busy.
   *
   * <p>The count is committed in a transaction of its own, suspending any transaction the caller is
   * already inside. Without that, every limit enforced within a request's own transaction would be
   * undone by that request failing -- and failing is exactly what the interesting requests do. A
   * wrong password rolls back, so an attacker guessing passwords would be refunded every attempt
   * and the budget would restrain nobody but the person typing correctly. Callers that hold no
   * transaction, such as the filters, are unaffected beyond the cost of one short transaction.
   *
   * @param bucketKey the caller's bucket, {@code "<scope>:<key>"} -- a client address for the
   *     anonymous families, a user id or a hashed email where the request has one
   * @param limit the number of requests allowed within one window
   * @param window the window length; the current window starts at the last multiple of it
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Decision record(String bucketKey, int limit, Duration window) {
    Instant now = Instant.now(clock);
    Instant windowStart = floorTo(now, window);
    Integer hits;
    try {
      hits = jdbc.queryForObject(UPSERT, Integer.class, bucketKey, Timestamp.from(windowStart));
    } catch (DataAccessException e) {
      log.warn(
          "Rate limit counter unavailable for bucket '{}'; allowing the request.", bucketKey, e);
      return new Decision(true, 0);
    }
    if (hits != null && hits <= limit) {
      return new Decision(true, 0);
    }
    long retryAfter = Duration.between(now, windowStart.plus(window)).toSeconds();
    return new Decision(false, Math.max(1, retryAfter));
  }

  private static Instant floorTo(Instant instant, Duration window) {
    long windowSeconds = Math.max(1, window.toSeconds());
    long epochSecond = instant.getEpochSecond();
    return Instant.ofEpochSecond(epochSecond - Math.floorMod(epochSecond, windowSeconds));
  }

  /**
   * @param allowed whether the request may proceed
   * @param retryAfterSeconds seconds until the current window ends, at least 1; meaningful only
   *     when {@code allowed} is false
   */
  public record Decision(boolean allowed, long retryAfterSeconds) {}
}
