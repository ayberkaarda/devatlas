package dev.devatlas.server.domain;

/** Origin of a blog post: written by hand, or drafted by the ingest pipeline. */
public enum BlogSource {
  MANUAL,
  AUTO
}
