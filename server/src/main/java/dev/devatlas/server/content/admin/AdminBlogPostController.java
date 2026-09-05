package dev.devatlas.server.content.admin;

import dev.devatlas.server.auth.AccessTokenClaims;
import dev.devatlas.server.common.PageResponse;
import dev.devatlas.server.content.admin.dto.AdminBlogPostResponse;
import dev.devatlas.server.content.admin.dto.AuditLogItemResponse;
import dev.devatlas.server.content.admin.dto.BlogTransitionRequest;
import dev.devatlas.server.content.admin.dto.CreateBlogPostRequest;
import dev.devatlas.server.content.admin.dto.UpdateBlogPostRequest;
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
 * {@code /admin/blog/posts} (§5.5.2). Role enforcement -- {@code EDITOR}/{@code ADMIN} generally,
 * {@code ADMIN}-only for {@code approve}/{@code reject}/{@code unpublish} -- happens in {@code
 * SecurityConfig}; the {@code EDITOR} restrictions that depend on a post's own state ({@code AUTO}
 * body edits, {@code AUTO} publication) are enforced in {@link AdminBlogPostService}, which is why
 * the caller's role is threaded through to {@link AdminBlogPostService#update}.
 */
@RestController
@RequestMapping("/api/v1/admin/blog/posts")
public class AdminBlogPostController {

  private final AdminBlogPostService service;

  public AdminBlogPostController(AdminBlogPostService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<AdminBlogPostResponse> create(
      @Valid @RequestBody CreateBlogPostRequest request,
      @AuthenticationPrincipal AccessTokenClaims caller) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, caller.userId()));
  }

  @GetMapping
  public PageResponse<AdminBlogPostResponse> list(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) List<String> sort,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) String q) {
    return service.list(page, size, sort, status, source, q);
  }

  @GetMapping("/{id}")
  public AdminBlogPostResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PatchMapping("/{id}")
  public AdminBlogPostResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateBlogPostRequest request,
      @AuthenticationPrincipal AccessTokenClaims caller) {
    return service.update(id, request, caller.role());
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/submit")
  public AdminBlogPostResponse submit(
      @PathVariable UUID id,
      @Valid @RequestBody BlogTransitionRequest request,
      @AuthenticationPrincipal AccessTokenClaims caller) {
    return service.submit(id, request, caller.userId());
  }

  @PostMapping("/{id}/approve")
  public AdminBlogPostResponse approve(
      @PathVariable UUID id,
      @Valid @RequestBody BlogTransitionRequest request,
      @AuthenticationPrincipal AccessTokenClaims caller) {
    return service.approve(id, request, caller.userId());
  }

  @PostMapping("/{id}/reject")
  public AdminBlogPostResponse reject(
      @PathVariable UUID id,
      @Valid @RequestBody BlogTransitionRequest request,
      @AuthenticationPrincipal AccessTokenClaims caller) {
    return service.reject(id, request, caller.userId());
  }

  @PostMapping("/{id}/publish")
  public AdminBlogPostResponse publish(
      @PathVariable UUID id,
      @Valid @RequestBody BlogTransitionRequest request,
      @AuthenticationPrincipal AccessTokenClaims caller) {
    return service.publish(id, request, caller.userId());
  }

  @PostMapping("/{id}/unpublish")
  public AdminBlogPostResponse unpublish(
      @PathVariable UUID id,
      @Valid @RequestBody BlogTransitionRequest request,
      @AuthenticationPrincipal AccessTokenClaims caller) {
    return service.unpublish(id, request, caller.userId());
  }

  @GetMapping("/{id}/audit-log")
  public PageResponse<AuditLogItemResponse> auditLog(
      @PathVariable UUID id,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return service.auditLog(id, page, size);
  }
}
