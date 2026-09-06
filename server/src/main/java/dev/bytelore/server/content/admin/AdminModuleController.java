package dev.bytelore.server.content.admin;

import dev.bytelore.server.content.admin.dto.AdminLessonResponse;
import dev.bytelore.server.content.admin.dto.AdminModuleResponse;
import dev.bytelore.server.content.admin.dto.CreateModuleRequest;
import dev.bytelore.server.content.admin.dto.ReorderRequest;
import dev.bytelore.server.content.admin.dto.UpdateModuleRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /admin/tracks/{trackId}/modules} and {@code /admin/modules} (§5.4.3). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminModuleController {

  private final AdminModuleService service;

  public AdminModuleController(AdminModuleService service) {
    this.service = service;
  }

  @PostMapping("/tracks/{trackId}/modules")
  public ResponseEntity<AdminModuleResponse> create(
      @PathVariable UUID trackId, @Valid @RequestBody CreateModuleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(trackId, request));
  }

  @PatchMapping("/modules/{id}")
  public AdminModuleResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateModuleRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/modules/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/modules/{id}/lessons/order")
  public List<AdminLessonResponse> reorderLessons(
      @PathVariable UUID id, @Valid @RequestBody ReorderRequest request) {
    return service.reorderLessons(id, request.orderedIds());
  }
}
