package dev.devatlas.server.domain;

/** Outcome of the independent second-request check that confirms a fetched source update. */
public enum VerifyStatus {
  PENDING,
  VERIFIED,
  REJECTED
}
