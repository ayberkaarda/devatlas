package dev.devatlas.server.domain;

/** One step of the blog ingest pipeline, recorded in {@link PipelineAuditLog}. */
public enum PipelineStep {
  FETCH,
  NORMALIZE,
  VERIFY,
  DRAFT,
  SUBMIT,
  APPROVE,
  REJECT,
  PUBLISH,
  UNPUBLISH
}
