package dev.devatlas.server.content.admin;

import dev.devatlas.server.content.admin.dto.SourceUpdateDetailResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /admin/source-updates/{id}} (§5.7). {@code EDITOR}, {@code ADMIN}. */
@RestController
@RequestMapping("/api/v1/admin/source-updates")
public class AdminSourceUpdateController {

  private final AdminReviewQueueService service;

  public AdminSourceUpdateController(AdminReviewQueueService service) {
    this.service = service;
  }

  @GetMapping("/{id}")
  public SourceUpdateDetailResponse get(@PathVariable UUID id) {
    return service.toSourceUpdateResponse(id);
  }
}
