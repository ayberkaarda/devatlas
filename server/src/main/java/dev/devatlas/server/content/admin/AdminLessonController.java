package dev.devatlas.server.content.admin;

import dev.devatlas.server.content.admin.dto.AdminCodeExampleResponse;
import dev.devatlas.server.content.admin.dto.AdminLessonResponse;
import dev.devatlas.server.content.admin.dto.CreateLessonRequest;
import dev.devatlas.server.content.admin.dto.ReorderRequest;
import dev.devatlas.server.content.admin.dto.UpdateLessonRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /admin/modules/{moduleId}/lessons} and {@code /admin/lessons} (§5.4.4). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminLessonController {

  private final AdminLessonService service;

  public AdminLessonController(AdminLessonService service) {
    this.service = service;
  }

  @PostMapping("/modules/{moduleId}/lessons")
  public ResponseEntity<AdminLessonResponse> create(
      @PathVariable UUID moduleId, @Valid @RequestBody CreateLessonRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(moduleId, request));
  }

  @GetMapping("/lessons/{id}")
  public AdminLessonResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PatchMapping("/lessons/{id}")
  public AdminLessonResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateLessonRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/lessons/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/lessons/{id}/code-examples/order")
  public List<AdminCodeExampleResponse> reorderCodeExamples(
      @PathVariable UUID id, @Valid @RequestBody ReorderRequest request) {
    return service.reorderCodeExamples(id, request.orderedIds());
  }
}
