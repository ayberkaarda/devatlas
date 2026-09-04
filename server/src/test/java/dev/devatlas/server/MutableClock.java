package dev.devatlas.server;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A {@link Clock} whose instant is set by the test rather than read from the wall clock.
 *
 * <p>Every place in the authentication layer that needs "now" -- a token lifetime, the refresh
 * rotation grace window, a last-write-wins comparison -- receives a {@link Clock} through
 * constructor injection rather than calling {@link Clock#systemUTC()} directly. That is what makes
 * substituting this fake possible without touching production code: it is wired in ahead of the
 * real {@code systemClock} bean with {@code @Primary} from a {@code @TestConfiguration} that lives
 * entirely under {@code src/test}.
 */
final class MutableClock extends Clock {

  private Instant instant;

  MutableClock(Instant initial) {
    this.instant = initial;
  }

  /** Moves the clock to an absolute instant, discarding whatever it read before. */
  void set(Instant instant) {
    this.instant = instant;
  }

  /** Moves the clock forward by the given amount. */
  void advance(Duration duration) {
    this.instant = this.instant.plus(duration);
  }

  @Override
  public ZoneId getZone() {
    return ZoneId.of("UTC");
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException("This suite only ever reads UTC.");
  }

  @Override
  public Instant instant() {
    return instant;
  }
}
