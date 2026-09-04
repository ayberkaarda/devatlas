package dev.devatlas.server.content.admin;

import dev.devatlas.server.common.PageResponse;
import dev.devatlas.server.content.admin.dto.AdminModuleResponse;
import dev.devatlas.server.content.admin.dto.AdminTrackResponse;
import dev.devatlas.server.content.admin.dto.CreateTrackRequest;
import dev.devatlas.server.content.admin.dto.ReorderRequest;
import dev.devatlas.server.content.admin.dto.UpdateTrackRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /admin/tracks} (§5.4.2). {@code EDITOR}/{@code ADMIN}, enforced by {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/admin/tracks")
public class AdminTrackController {

  private final AdminTrackService service;

  public AdminTrackController(AdminTrackService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<AdminTrackResponse> create(@Valid @RequestBody CreateTrackRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
  }

  @GetMapping
  public PageResponse<AdminTrackResponse> list(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) List<String> sort,
      @RequestParam(required = false) Boolean published) {
    return service.list(page, size, sort, published);
  }

  @GetMapping("/{id}")
  public AdminTrackResponse get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PatchMapping("/{id}")
  public AdminTrackResponse update(
      @PathVariable UUID id, @Valid @RequestBody UpdateTrackRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/modules/order")
  public List<AdminModuleResponse> reorderModules(
      @PathVariable UUID id, @Valid @RequestBody ReorderRequest request) {
    return service.reorderModules(id, request.orderedIds());
  }
}
