package dev.bytelore.server.sync;

import java.time.Duration;
import java.time.Instant;

/**
 * The two decisions the progress sync endpoint makes about a single item, kept free of persistence
 * and of the web layer so they can be exercised directly.
 *
 * <p>Both are pure functions of their arguments. That matters more than it looks: the rules below
 * are the whole of the convergence guarantee between a desktop client, a browser and the server,
 * and a rule that can only be checked by standing up a database and an HTTP round trip is a rule
 * whose edge cases do not get checked.
 */
public final class ProgressReconciler {

  /**
   * How far ahead of server time a client timestamp may be before it is treated as a broken clock
   * rather than as a real moment.
   *
   * <p>It is not zero, because clock skew of seconds to minutes is ordinary and rewriting every
   * such value would destroy legitimate ordering between two devices that are both roughly right. A
   * full day is comfortably beyond any skew a working clock produces and comfortably short of the
   * years a dead battery or a mistyped year produces.
   */
  static final Duration FUTURE_TOLERANCE = Duration.ofHours(24);

  private ProgressReconciler() {}

  /**
   * Applies the clock clamp to one item's timestamps.
   *
   * <p>A device whose clock is badly wrong -- a dead battery, a mistyped date, a bad timezone setup
   * -- is not misbehaving on purpose, and the completions it recorded are real. Rejecting its
   * writes would deadlock it permanently: every write refused, progress never syncing, and nothing
   * the person can do inside the application to break out. Clamping accepts the work and discards
   * only the impossible part of the metadata, and the one thing it must prevent -- a far-future
   * timestamp winning every comparison forever -- it prevents completely, because a stored value
   * can never exceed server time.
   *
   * <p>The two timestamps are judged independently against the same threshold: an item may carry a
   * plausible {@code clientUpdatedAt} and an impossible {@code completedAt}, and each is a separate
   * claim about when something happened.
   *
   * @param completedAt when the lesson was completed, or {@code null} for an explicit
   *     un-completion, which is a real action and is preserved as such rather than read as absence
   * @param clientUpdatedAt the client's own clock, the value last-write-wins compares on
   * @param serverTime the instant this batch is being processed at
   */
  public static Clamped clamp(Instant completedAt, Instant clientUpdatedAt, Instant serverTime) {
    Instant threshold = serverTime.plus(FUTURE_TOLERANCE);

    boolean clientAhead = clientUpdatedAt.isAfter(threshold);
    boolean completedAhead = completedAt != null && completedAt.isAfter(threshold);

    return new Clamped(
        completedAhead ? serverTime : completedAt,
        clientAhead ? serverTime : clientUpdatedAt,
        clientAhead || completedAhead);
  }

  /**
   * Whether an incoming item overwrites the row already stored.
   *
   * <p>Strictly greater, never greater-or-equal. Equality keeping the stored row is what makes a
   * replay of the same batch a no-op instead of a rewrite, and a client that resends its pending
   * queue after a lost response is the common case, not the exceptional one.
   */
  public static boolean wins(Instant incomingClientUpdatedAt, Instant storedClientUpdatedAt) {
    return incomingClientUpdatedAt.isAfter(storedClientUpdatedAt);
  }

  /**
   * One item's timestamps after the clamp, and whether the clamp changed anything.
   *
   * @param completedAt the value to store, clamped if it was implausibly far ahead
   * @param clientUpdatedAt the value to compare and store, clamped the same way
   * @param clamped true when either timestamp was rewritten; reported to the client so it can
   *     surface a "your device clock looks wrong" hint and write the winning value back
   */
  public record Clamped(Instant completedAt, Instant clientUpdatedAt, boolean clamped) {}
}
