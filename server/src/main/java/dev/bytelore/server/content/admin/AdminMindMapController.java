package dev.bytelore.server.content.admin;

import dev.bytelore.server.content.admin.dto.AdminMindMapResponse;
import dev.bytelore.server.content.admin.dto.MindMapUpsertRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /admin/tracks/{trackId}/mindmap} (§5.4.6). */
@RestController
@RequestMapping("/api/v1/admin/tracks/{trackId}/mindmap")
public class AdminMindMapController {

  private final AdminMindMapService service;

  public AdminMindMapController(AdminMindMapService service) {
    this.service = service;
  }

  @GetMapping
  public AdminMindMapResponse get(@PathVariable UUID trackId) {
    return service.get(trackId);
  }

  @PutMapping
  public ResponseEntity<AdminMindMapResponse> upsert(
      @PathVariable UUID trackId, @Valid @RequestBody MindMapUpsertRequest request) {
    AdminMindMapService.UpsertOutcome outcome = service.upsert(trackId, request);
    HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(outcome.response());
  }

  @DeleteMapping
  public ResponseEntity<Void> delete(@PathVariable UUID trackId) {
    service.delete(trackId);
    return ResponseEntity.noContent().build();
  }
}
