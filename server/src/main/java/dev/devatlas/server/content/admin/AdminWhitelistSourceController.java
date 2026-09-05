package dev.devatlas.server.content.admin;

import dev.devatlas.server.auth.AccessTokenClaims;
import dev.devatlas.server.common.PageResponse;
import dev.devatlas.server.content.admin.dto.CreateWhitelistSourceRequest;
import dev.devatlas.server.content.admin.dto.UpdateWhitelistSourceRequest;
import dev.devatlas.server.content.admin.dto.WhitelistSourceFetchResponse;
import dev.devatlas.server.content.admin.dto.WhitelistSourceResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /admin/whitelist-sources} (§5.7). {@code ADMIN}-only for every verb, in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/admin/whitelist-sources")
public class AdminWhitelistSourceController {

  private final AdminWhitelistSourceService service;

  public AdminWhitelistSourceController(AdminWhitelistSourceService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<WhitelistSourceResponse> create(
      @Valid @RequestBody CreateWhitelistSourceRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  @GetMapping
  public PageResponse<WhitelistSourceResponse> list(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) List<String> sort) {
    return service.list(page, size, sort);
  }

  @GetMapping("/{id}")
  public WhitelistSourceResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PatchMapping("/{id}")
  public WhitelistSourceResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateWhitelistSourceRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/fetch")
  public WhitelistSourceFetchResponse fetch(
      @PathVariable UUID id, @AuthenticationPrincipal AccessTokenClaims caller) {
    return service.manualFetch(id, caller.userId());
  }
}
