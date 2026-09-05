package dev.devatlas.server.pipeline;

import java.util.UUID;

/** What happened to one feed item after {@link PipelineItemProcessor} ran the chain against it. */
public record ItemOutcome(Kind kind, UUID sourceUpdateId, Rejection rejection) {

  public enum Kind {
    CREATED,
    DUPLICATE,
    REJECTED
  }

  public static ItemOutcome created(UUID sourceUpdateId) {
    return new ItemOutcome(Kind.CREATED, sourceUpdateId, null);
  }

  public static ItemOutcome duplicate() {
    return new ItemOutcome(Kind.DUPLICATE, null, null);
  }

  public static ItemOutcome rejected(Rejection rejection) {
    return new ItemOutcome(Kind.REJECTED, null, rejection);
  }
}
