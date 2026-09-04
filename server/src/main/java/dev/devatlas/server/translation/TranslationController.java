package dev.devatlas.server.translation;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.domain.TranslationEntityType;
import dev.devatlas.server.translation.dto.TranslationGetResponse;
import dev.devatlas.server.translation.dto.TranslationUpsertRequest;
import dev.devatlas.server.translation.dto.TranslationWriteResponse;
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

/**
 * {@code /admin/translations/{entityType}/{entityId}[/{locale}]} (§5.6). {@code entityType} is
 * taken as a raw path segment rather than a Spring-converted enum specifically so that an
 * unsupported value -- including {@code MIND_MAP}, which is not translatable (§5.2.4) -- is
 * reported as {@code 400 ENTITY_TYPE_UNSUPPORTED} rather than the framework's generic {@code 400
 * MALFORMED_REQUEST} for an unparseable path variable.
 */
@RestController
@RequestMapping("/api/v1/admin/translations")
public class TranslationController {

  private final TranslationService service;

  public TranslationController(TranslationService service) {
    this.service = service;
  }

  @GetMapping("/{entityType}/{entityId}")
  public TranslationGetResponse get(@PathVariable String entityType, @PathVariable UUID entityId) {
    return service.get(parseEntityType(entityType), entityId);
  }

  @PutMapping("/{entityType}/{entityId}/{locale}")
  public ResponseEntity<TranslationWriteResponse> upsert(
      @PathVariable String entityType,
      @PathVariable UUID entityId,
      @PathVariable String locale,
      @Valid @RequestBody TranslationUpsertRequest request) {
    TranslationService.UpsertOutcome outcome =
        service.upsert(parseEntityType(entityType), entityId, locale, request);
    return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(outcome.response());
  }

  @DeleteMapping("/{entityType}/{entityId}/{locale}")
  public ResponseEntity<Void> delete(
      @PathVariable String entityType, @PathVariable UUID entityId, @PathVariable String locale) {
    service.delete(parseEntityType(entityType), entityId, locale);
    return ResponseEntity.noContent().build();
  }

  private static TranslationEntityType parseEntityType(String value) {
    try {
      return TranslationEntityType.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          ErrorCode.ENTITY_TYPE_UNSUPPORTED,
          "'%s' is not a translatable entity type.".formatted(value));
    }
  }
}
