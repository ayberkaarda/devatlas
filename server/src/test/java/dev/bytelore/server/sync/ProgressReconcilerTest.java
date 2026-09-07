package dev.bytelore.server.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The two rules that decide a sync item's fate, exercised without a database or an HTTP round trip.
 *
 * <p>They are the whole of the convergence guarantee between the desktop client, the browser build
 * and the server, and the interesting cases -- a timestamp a year in the future, two writes landing
 * in the same millisecond -- are the ones an integration test can only reach by contriving them.
 * Here they are just arguments.
 */
class ProgressReconcilerTest {

  private static final Instant SERVER_TIME = Instant.parse("2026-09-07T12:00:00.000Z");

  @Test
  void aPlausibleTimestampIsLeftExactlyAsItArrived() {
    Instant completedAt = SERVER_TIME.minus(Duration.ofHours(3));
    Instant clientUpdatedAt = SERVER_TIME.minus(Duration.ofHours(2));

    ProgressReconciler.Clamped result =
        ProgressReconciler.clamp(completedAt, clientUpdatedAt, SERVER_TIME);

    assertThat(result.clamped()).isFalse();
    assertThat(result.completedAt()).isEqualTo(completedAt);
    assertThat(result.clientUpdatedAt()).isEqualTo(clientUpdatedAt);
  }

  /**
   * Ordinary skew survives. Clamping every future value would destroy legitimate ordering between
   * two devices that are both roughly right, which is most pairs of devices.
   */
  @Test
  void aTimestampInsideTheToleranceIsNotClampedEvenThoughItIsInTheFuture() {
    Instant nearFuture = SERVER_TIME.plus(Duration.ofHours(23));

    ProgressReconciler.Clamped result =
        ProgressReconciler.clamp(nearFuture, nearFuture, SERVER_TIME);

    assertThat(result.clamped()).isFalse();
    assertThat(result.clientUpdatedAt()).isEqualTo(nearFuture);
    assertThat(result.completedAt()).isEqualTo(nearFuture);
  }

  /** Exactly on the boundary is still inside it: the rule is "more than 24 hours ahead". */
  @Test
  void aTimestampExactlyAtTheToleranceBoundaryIsNotClamped() {
    Instant boundary = SERVER_TIME.plus(ProgressReconciler.FUTURE_TOLERANCE);

    ProgressReconciler.Clamped result = ProgressReconciler.clamp(boundary, boundary, SERVER_TIME);

    assertThat(result.clamped()).isFalse();
    assertThat(result.clientUpdatedAt()).isEqualTo(boundary);
  }

  @Test
  void aFarFutureClientTimestampIsReplacedWithServerTime() {
    Instant farFuture = SERVER_TIME.plus(Duration.ofDays(365));

    ProgressReconciler.Clamped result =
        ProgressReconciler.clamp(SERVER_TIME.minus(Duration.ofHours(1)), farFuture, SERVER_TIME);

    assertThat(result.clamped()).isTrue();
    assertThat(result.clientUpdatedAt()).isEqualTo(SERVER_TIME);
    // The plausible half of the item is untouched. Clamping discards the impossible metadata, not
    // the work.
    assertThat(result.completedAt()).isEqualTo(SERVER_TIME.minus(Duration.ofHours(1)));
  }

  /**
   * The two timestamps are judged independently. An item can carry a believable "I changed this
   * just now" alongside an impossible "I completed it in 2124", and each is a separate claim.
   */
  @Test
  void aFarFutureCompletedAtIsClampedOnItsOwn() {
    Instant farFuture = SERVER_TIME.plus(Duration.ofDays(400));

    ProgressReconciler.Clamped result =
        ProgressReconciler.clamp(farFuture, SERVER_TIME.minus(Duration.ofMinutes(5)), SERVER_TIME);

    assertThat(result.clamped()).isTrue();
    assertThat(result.completedAt()).isEqualTo(SERVER_TIME);
    assertThat(result.clientUpdatedAt()).isEqualTo(SERVER_TIME.minus(Duration.ofMinutes(5)));
  }

  /** A clamped value can never exceed server time, which is what stops it winning forever. */
  @Test
  void aClampedValueNeverExceedsServerTime() {
    ProgressReconciler.Clamped result =
        ProgressReconciler.clamp(
            SERVER_TIME.plus(Duration.ofDays(9999)),
            SERVER_TIME.plus(Duration.ofDays(9999)),
            SERVER_TIME);

    assertThat(result.completedAt()).isBeforeOrEqualTo(SERVER_TIME);
    assertThat(result.clientUpdatedAt()).isBeforeOrEqualTo(SERVER_TIME);
  }

  /** Null is an explicit un-completion, not missing data, and the clamp leaves it alone. */
  @Test
  void aNullCompletedAtStaysNullThroughTheClamp() {
    ProgressReconciler.Clamped result =
        ProgressReconciler.clamp(null, SERVER_TIME.plus(Duration.ofDays(30)), SERVER_TIME);

    assertThat(result.completedAt()).isNull();
    assertThat(result.clamped()).isTrue();
    assertThat(result.clientUpdatedAt()).isEqualTo(SERVER_TIME);
  }

  @Test
  void aStrictlyNewerItemWins() {
    assertThat(ProgressReconciler.wins(SERVER_TIME.plusMillis(1), SERVER_TIME)).isTrue();
  }

  /**
   * Equality keeps the stored row. This is the property that makes a replayed batch a no-op -- and
   * a client resending its pending queue after a lost response is the common case, not the
   * exception.
   */
  @Test
  void anEqualTimestampLosesSoThatAReplayChangesNothing() {
    assertThat(ProgressReconciler.wins(SERVER_TIME, SERVER_TIME)).isFalse();
  }

  @Test
  void anOlderItemLoses() {
    assertThat(ProgressReconciler.wins(SERVER_TIME.minusMillis(1), SERVER_TIME)).isFalse();
  }
}
