package dev.devatlas.server.domain;

/** Editorial state of a blog post as it moves through the review pipeline. */
public enum BlogStatus {
  DRAFT,
  PENDING_REVIEW,
  PUBLISHED,
  REJECTED
}
