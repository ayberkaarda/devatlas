package dev.bytelore.server.sync;

import dev.bytelore.server.auth.AccessTokenClaims;
import dev.bytelore.server.sync.dto.ProgressPullResponse;
import dev.bytelore.server.sync.dto.ProgressSyncRequest;
import dev.bytelore.server.sync.dto.ProgressSyncResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Progress sync, both directions (§5.8).
 *
 * <p>The caller's identity comes from the verified access token and from nowhere else. Neither
 * endpoint accepts a user identifier in a body, a path or a query parameter, so there is no shape
 * of request that reads or writes somebody else's rows -- a property worth having by construction
 * rather than by a check that a later change could forget.
 */
@RestController
@RequestMapping("/api/v1/sync")
public class ProgressSyncController {

  private final ProgressSyncService service;

  public ProgressSyncController(ProgressSyncService service) {
    this.service = service;
  }

  @PostMapping("/progress")
  public ProgressSyncResponse push(
      @AuthenticationPrincipal AccessTokenClaims caller,
      @Valid @RequestBody ProgressSyncRequest request) {
    return service.push(caller.userId(), request);
  }

  /**
   * {@code since} is taken as a string and parsed by the service rather than bound straight to an
   * {@code Instant}. The framework's own string-to-Instant conversion accepts a different set of
   * inputs than this API's request bodies do, and a cursor that is legal in a query parameter but
   * illegal in a body -- or the reverse -- is a difference no client should have to know about.
   */
  @GetMapping("/progress")
  public ProgressPullResponse pull(
      @AuthenticationPrincipal AccessTokenClaims caller,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) List<String> sort,
      @RequestParam(required = false) String since) {
    return service.pull(caller.userId(), page, size, sort, since);
  }
}
