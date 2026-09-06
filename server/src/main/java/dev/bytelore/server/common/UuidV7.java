package dev.bytelore.server.common;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generator for version 7 UUIDs (RFC 9562).
 *
 * <p>A v7 value is a 48-bit Unix millisecond timestamp followed by random bits, so identifiers
 * generated over time sort in creation order. That is what makes them index like a sequence -- a
 * random v4 primary key scatters inserts across the whole B-tree and turns every insert into a page
 * split somewhere cold -- while still being non-enumerable in a public API and stable across
 * environments, so seed data, development, CI and production can all agree on the same identifier.
 *
 * <p>{@link UUID} offers no v7 factory, so the layout is assembled here:
 *
 * <pre>
 *   bits  0-47   unix_ts_ms   milliseconds since the epoch, big endian
 *   bits 48-51   version      literal 0b0111
 *   bits 52-63   rand_a       random
 *   bits 64-65   variant      literal 0b10
 *   bits 66-127  rand_b       random
 * </pre>
 */
public final class UuidV7 {

  private static final SecureRandom RANDOM = new SecureRandom();

  private UuidV7() {}

  /** Returns a fresh version 7 UUID stamped with the current wall-clock millisecond. */
  public static UUID randomUuid() {
    return fromEpochMilli(System.currentTimeMillis());
  }

  /**
   * Returns a version 7 UUID stamped with the supplied epoch millisecond. Exposed so that tests can
   * assert the time-ordering property without racing the clock.
   */
  public static UUID fromEpochMilli(long epochMilli) {
    byte[] random = new byte[10];
    RANDOM.nextBytes(random);

    long mostSignificant = (epochMilli & 0xFFFFFFFFFFFFL) << 16;
    mostSignificant |= (long) (random[0] & 0x0F) << 8;
    mostSignificant |= random[1] & 0xFFL;
    // Version 7 in bits 48-51.
    mostSignificant = (mostSignificant & ~0xF000L) | 0x7000L;

    long leastSignificant = 0L;
    for (int i = 2; i < 10; i++) {
      leastSignificant = (leastSignificant << 8) | (random[i] & 0xFFL);
    }
    // Variant 0b10 in the two most significant bits of the low half.
    leastSignificant = (leastSignificant & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;

    return new UUID(mostSignificant, leastSignificant);
  }
}
