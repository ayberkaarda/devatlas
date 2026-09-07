package dev.bytelore.server.sync;

/** What the server did with one item of a progress sync batch. */
public enum ProgressSyncStatus {

  /** Stored: either inserted, or an overwrite of a strictly older row. */
  APPLIED,

  /** Discarded: the stored row's timestamp is the same or newer, so it stands. */
  STALE,

  /** Refused: the lesson the item refers to does not resolve. The only per-item rejection. */
  REJECTED
}
