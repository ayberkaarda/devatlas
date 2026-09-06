package dev.bytelore.server.content.admin;

import dev.bytelore.server.content.admin.dto.AdminCodeExampleResponse;
import dev.bytelore.server.content.admin.dto.CreateCodeExampleRequest;
import dev.bytelore.server.content.admin.dto.UpdateCodeExampleRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /admin/lessons/{lessonId}/code-examples} and {@code /admin/code-examples} (§5.4.5). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCodeExampleController {

  private final AdminCodeExampleService service;

  public AdminCodeExampleController(AdminCodeExampleService service) {
    this.service = service;
  }

  @PostMapping("/lessons/{lessonId}/code-examples")
  public ResponseEntity<AdminCodeExampleResponse> create(
      @PathVariable UUID lessonId, @Valid @RequestBody CreateCodeExampleRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(lessonId, request));
  }

  @PatchMapping("/code-examples/{id}")
  public AdminCodeExampleResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateCodeExampleRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/code-examples/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
