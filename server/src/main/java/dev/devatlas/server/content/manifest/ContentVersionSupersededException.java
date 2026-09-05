package dev.devatlas.server.content.manifest;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;

/**
 * A package was requested at a {@code content_version} older than the current one (§4.4).
 *
 * <p>It carries the current version because the protocol requires the client to be able to re-plan
 * without a second round trip: the download engine treats this as "re-plan, do not consume an
 * attempt" (§11), and re-planning needs the number. Only the current version of an entity is
 * served; historical packages are not retained, because a stale client can re-fetch a manifest
 * cheaply while storing every past package is not cheap at all.
 */
public class ContentVersionSupersededException extends ApiException {

  private static final long serialVersionUID = 1L;

  private final int currentVersion;

  public ContentVersionSupersededException(int requestedVersion, int currentVersion) {
    super(
        ErrorCode.CONTENT_VERSION_SUPERSEDED,
        "Version %d is no longer current; the current version is %d."
            .formatted(requestedVersion, currentVersion));
    this.currentVersion = currentVersion;
  }

  public int currentVersion() {
    return currentVersion;
  }
}
