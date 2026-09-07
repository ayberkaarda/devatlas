# 0003. Progress synchronisation conflict rule: last-write-wins on `client_updated_at`

## Status

Accepted

## Context

`UserProgress` records one fact per lesson per user: whether it is completed,
and when. A single account can be signed in on more than one device — the
refresh-token contract (`docs/protocol/rest-api.md` §3.1) carries a
`device_label` on every token precisely because more than one device is
expected — and each device keeps writing completion state locally whether or
not it can currently reach the server:

- **Writes are local-first, unconditionally.** `progress_mark(lessonId,
  completed)` (`docs/protocol/tauri-commands.md` §5) records completion in
  SQLite with `clientUpdatedAt` set to now and never checks a token. This is
  not an optimisation; it is a hard product requirement (§3.5 of the REST
  contract): the desktop client must be fully usable offline, and a
  completion is written before any server is consulted, if one is reachable
  at all.
- **Reconciliation happens later, in a batch, in both directions.** Angular
  calls `POST /sync/progress` to push what accumulated locally and `GET
  /sync/progress` to pull what other devices pushed in the meantime. A batch
  may hold weeks of offline writes; the two calls are the only points at
  which two devices' views of the same lesson actually meet.
- **A completion is never deleted, by any path.** §2.10 of the REST contract
  is explicit that `UserProgress` rows survive even a soft-deleted lesson,
  and `docs/protocol/content-sync.md` §10 repeats the rule for the local
  replica: deleting downloaded content never touches `user_progress`. Whatever
  conflict rule is chosen, it resolves *values on an existing row*; it never
  has to decide whether the row should exist.
- **A rejected row must stop being resent.** `progress_apply_results`
  (`tauri-commands.md` §5) marks a row `REJECTED`/`ORPHANED` when the server
  cannot resolve its `lessonId`, and such a row is never included in a future
  batch. Without this, a client that once wrote against a lesson later
  deleted upstream would carry that same dead row forever. This is a
  detail of *how the conflict rule terminates*, not the rule itself, and it
  has to be decided alongside it: any resolution scheme needs an answer for
  "this row can never be resolved," not only for "this row conflicted."

The one figure that has to be reconciled is a single (`completed_at`,
`client_updated_at`) pair, and `completed_at: null` is itself a real,
syncable value — "explicitly marked incomplete" — not the absence of one
(REST contract §5.8.1, store schema comment in `desktop/src-tauri/src/store.rs`
on `user_progress`).

## Options considered

### A. Vector clock or per-device version counter

Each device keeps a counter (or a clock entry per device it has seen) and a
write carries the full vector; the server merges by comparing vectors and can
detect true concurrent writes that neither happened cleanly before the other.

- Plus: it is the textbook mechanism for detecting concurrent writes without
  trusting any clock, and it degrades gracefully to "last write wins" when
  vectors are comparable.
- Minus: it exists to answer a question this product does not ask. A vector
  clock's payoff is telling the caller *when it cannot decide* — that two
  writes are genuinely concurrent and must be merged or handed to a human.
  For a single boolean-shaped fact (done / not done, as of some time) there
  is nothing to merge: a concurrent completion and a concurrent
  un-completion are still just two candidate values, and something still has
  to pick one. The vector clock adds bookkeeping (per-device state, resolved
  once per device the row has ever touched) to a decision it does not
  actually change the shape of.
- Minus: it would have to be stored somewhere, per row, across every device
  that has ever touched a lesson — SQLite on one side, Postgres on the other
  — for a value that is not exposed to a person in any V1 screen; nothing in
  the product asks "was this a genuine conflict."

### B. Arrival-order-wins (whichever batch reaches the server first)

The server simply accepts whatever it receives, overwriting the previous
value, with no timestamp comparison at all.

- Plus: no clock trust needed, no clamping logic.
- Minus: this is the rule the product's own offline requirement rules out
  directly. §3.5 of the REST contract exists because the desktop client can
  be offline for weeks; a laptop that reconnects after three weeks and
  flushes its queued batch would, under arrival order, silently overwrite
  completions a phone recorded yesterday, because the laptop's *network
  request* arrived after the phone's, even though the laptop's underlying
  *event* happened first. Rejected outright — it inverts the guarantee
  offline support is supposed to give.

### C. Per-field merge

Treat `completed_at` and `client_updated_at` as independently mergeable
fields rather than one unit.

- Minus: there is only one fact here (a lesson is or is not complete, as of
  some time) and `client_updated_at` is metadata about that one fact, not a
  second independent field. Merging them separately can produce a completion
  timestamp from one device paired with the "not completed" flag from
  another, a combination that was never true on either device at any
  instant. Rejected as incoherent for a single-valued fact, not merely
  unnecessary.

### D. Last-write-wins on `client_updated_at`, with server-side clock clamping

The rule actually shipped (REST contract §5.8.1): the row with the strictly
greater `client_updated_at` wins; equal timestamps keep the existing row.
Before comparison, a `client_updated_at` more than 24 hours ahead of server
time is replaced with server time and the item is processed with the clamped
value, reported back as `clamped: true`.

- Plus: matches the actual shape of the data — one timestamped value per
  row — with the simplest possible comparison, symmetric on both sides of
  the wire (REST contract §5.8.2: "The client applies the same last-write-wins
  rule locally... The rule is symmetric on both sides of the wire, which is
  what makes repeated syncs converge").
- Plus: converges under replay. Re-uploading the same batch twice, or pulling
  the same row twice, produces the same end state, because the comparison is
  a pure function of the two timestamps — no counter to double-increment, no
  ordering to get wrong on retry.
- Plus: the clamp closes the one failure mode a bare last-write-wins rule
  would otherwise have — a device with a wrong clock (dead CMOS battery, bad
  timezone) writing a far-future timestamp that would then win every future
  comparison forever, on every device, permanently. Clamping to `min(client,
  server)` bounds the stored value at server time, so the worst a bad clock
  can do is make its own write look like it happened "now," never in the
  future. The REST contract's own justification is adopted unchanged here:
  clamping is chosen over rejecting the item outright because rejection would
  put a badly-clocked device in a permanent deadlock — every future write
  from it refused, its user's real completions never syncing, with nothing
  the user can do about it from inside the application.
- Minus, accepted: a genuine loss is possible and is not hidden. If a device
  with a systematically slow clock completes a lesson *after* another device
  did, but its `client_updated_at` reads earlier because its clock is behind,
  the earlier-looking write is discarded (`STALE`) even though it happened
  later in real time. There is no field in this scheme that distinguishes
  "this device's clock is untrustworthy" from "this write genuinely happened
  first." Clamping only bounds a clock that runs *fast*; a clock that runs
  *slow* is not detected or corrected at all, because a slow clock never
  produces a value the 24-hour-ahead check can see.
- Minus, accepted: the rule cannot express "merge the outcome," only "pick a
  winner." A user who marks a lesson complete on a phone and then, on a
  laptop with a stale local copy, un-marks a *different* lesson does not lose
  data — those are different rows — but if the *same* lesson is toggled on
  one device and off on another within the reconciliation window, the loser's
  toggle is gone, not recorded anywhere, not surfaced to the user. This is
  judged acceptable specifically because progress is a low-stakes,
  single-user artifact: unlike the optimistic-locking `version` field used
  for authored content (REST contract §2.6), where a lost update destroys
  someone's editorial work, a lost progress toggle costs the user re-clicking
  "mark complete" once they notice. The contract deliberately draws this same
  line one level up, for preferences (`PATCH /auth/me`, no `version`, last
  write wins) with the identical reasoning: "a `409` on a theme toggle is a
  worse outcome than the write it prevents." Progress sync inherits that
  judgment for the same kind of low-stakes field.

## Decision

**Option D.** Last-write-wins on `client_updated_at`, with the server
clamping a client timestamp more than 24 hours ahead of server time before
comparing, applied symmetrically on push (`POST /sync/progress`) and pull
(`GET /sync/progress`).

The deciding context is the shape of the product this protects: a single
user, on a small number of personal devices, converging a value that is
binary in effect (a lesson is complete, or it is explicitly marked
incomplete, as of a timestamp). That context is what makes the cheap rule
sufficient — there is no multi-writer authoring scenario here the way there
is for a lesson body, and no third party's work to protect. It is also,
honestly, what limits the rule: the same context that makes conflicts rare
and low-stakes is the only reason a rule this simple is acceptable, and nothing
about it would be adequate for content two independent editors could write to
at once. That harder problem already has its own, different answer (`version`
and `409 VERSION_CONFLICT`) precisely because the stakes there are different.

## Consequences

### Positive

- One comparison, one column, no per-device state to store or expire.
  `progress_apply_results`'s three outcomes (`APPLIED`, `STALE`, `REJECTED`)
  map directly onto the three branches of the rule (new/overwritten,
  discarded, unresolvable) with nothing left over.
- Offline-for-weeks is a non-event: a stale device's queued batch is
  reconciled entry by entry against whatever the server now holds, and the
  outcome is identical to reconciling it immediately, because the rule does
  not depend on when the request arrives, only on the timestamp inside it.
- Silent convergence: `clamped: true` and a returned
  `server_client_updated_at` let a client correct its local row without a
  second round trip, and repeated syncs are idempotent by construction.
- `REJECTED` rows terminate cleanly (`ORPHANED`, never resent), so the queue
  cannot grow without bound around a lesson that no longer exists.

### Negative

- A device with a clock that runs slow (as opposed to fast) has no
  correction mechanism; its writes can lose to genuinely later writes from a
  correctly-clocked device, or win over genuinely earlier ones, and neither
  outcome is detected or logged the way a fast clock's clamp is.
- Two devices toggling the *same* lesson within one reconciliation window
  produce a winner and a silently discarded loser, with no record that a
  conflict occurred at all — by design, since the batch response reports
  `STALE`, not "conflicted."
- The rule only works because the conflicting value is small and
  low-consequence. It is not a template to reach for if a future feature
  needs two devices to reconcile something richer than a timestamped
  boolean.

### Follow-up

- A device that clamps on every sync has, per the REST contract's own note,
  "a real problem" worth surfacing; whether the desktop or web client
  actually shows a "check your device clock" hint from repeated
  `clamped: true` responses is a UI decision not yet made and not covered by
  this ADR.
- No test exists yet in this repository (frontend or server) that exercises
  the slow-clock loss case described above; the determinism guarantees in
  `docs/protocol/content-sync.md` do not extend to progress sync, which is a
  timestamp comparison, not a content hash.
