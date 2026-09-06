package dev.bytelore.server.pipeline;

import java.util.UUID;

/** What happened to one feed item after {@link PipelineItemProcessor} ran the chain against it. */
public record ItemOutcome(Kind kind, UUID sourceUpdateId, Rejection rejection, Deferral deferral) {

  public enum Kind {
    CREATED,
    DUPLICATE,
    REJECTED,
    /**
     * Skipped for this cycle by a transient failure, not a fact about the item; see {@link
     * Deferral}.
     */
    DEFERRED
  }

  public static ItemOutcome created(UUID sourceUpdateId) {
    return new ItemOutcome(Kind.CREATED, sourceUpdateId, null, null);
  }

  public static ItemOutcome duplicate() {
    return new ItemOutcome(Kind.DUPLICATE, null, null, null);
  }

  public static ItemOutcome rejected(Rejection rejection) {
    return new ItemOutcome(Kind.REJECTED, null, rejection, null);
  }

  public static ItemOutcome deferred(Deferral deferral) {
    return new ItemOutcome(Kind.DEFERRED, null, null, deferral);
  }
}
