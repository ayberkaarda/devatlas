package dev.bytelore.server.content.admin;

import dev.bytelore.server.common.PageResponse;
import dev.bytelore.server.content.admin.dto.AdminBlogPostResponse;
import dev.bytelore.server.content.admin.dto.ReviewQueueDetailResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /admin/review-queue} (§5.7). {@code EDITOR} may read (so an editor can prepare a
 * recommendation); the approve/reject decision stays {@code ADMIN}-only, enforced on the blog post
 * transition endpoints themselves, not here.
 */
@RestController
@RequestMapping("/api/v1/admin/review-queue")
public class AdminReviewQueueController {

  private final AdminReviewQueueService service;

  public AdminReviewQueueController(AdminReviewQueueService service) {
    this.service = service;
  }

  @GetMapping
  public PageResponse<AdminBlogPostResponse> list(
      @RequestParam(required = false) String source,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) List<String> sort) {
    return service.list(source, page, size, sort);
  }

  @GetMapping("/{postId}")
  public ReviewQueueDetailResponse detail(@PathVariable UUID postId) {
    return service.detail(postId);
  }
}
