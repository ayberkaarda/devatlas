package dev.devatlas.server.content.manifest;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/content/{entityType}/{entityId}?version=N} -- the package endpoint (§4.3).
 *
 * <p>The body is the stored canonical bytes, returned verbatim as a resource. Returning a resource
 * rather than a serialized object is what gives the endpoint HTTP {@code Range} support for free,
 * and {@code Range} is what the download engine resumes an interrupted transfer with (§7).
 *
 * <p>Two headers here are load-bearing rather than decorative:
 *
 * <ul>
 *   <li>{@code Content-Encoding: identity}, always. A range applies to the bytes of the
 *       representation that was selected, so a client that received a compressed first response and
 *       stored <i>N</i> decoded bytes cannot ask for {@code bytes=N-} and get what it expects.
 *       Identity also makes {@code Content-Length} equal the manifest's {@code size_bytes} exactly,
 *       which lets the engine detect truncation before it bothers hashing (§3.2b).
 *   <li>{@code Cache-Control: public, max-age=31536000, immutable}. The pair {@code (entity_id,
 *       version)} addresses content that can never change, so it can be cached for a year; the
 *       {@code ETag} is the digest, a strong validator, because any transformation that changes the
 *       bytes changes it.
 * </ul>
 */
@RestController
public class ContentPackageController {

  private static final MediaType JSON_UTF8 =
      new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

  private final ContentPackageService service;

  public ContentPackageController(ContentPackageService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/content/{entityType}/{entityId}")
  public ResponseEntity<Resource> getPackage(
      @PathVariable String entityType,
      @PathVariable String entityId,
      @RequestParam("version") int version,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    ContentEntityType type =
        ContentEntityType.fromPathToken(entityType)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.INVALID_PARAMETER,
                        "Entity type '%s' is not downloadable; expected 'lesson' or 'mind_map'."
                            .formatted(entityType)));
    ServedPackage served = service.load(type, parseEntityId(type, entityId), version);

    // Preconditions are evaluated only once the requested representation has been identified, so
    // an If-Match check never masks the 404 or 409 that version negotiation would have reported.
    if (ifMatch != null && !ConditionalRequests.matches(ifMatch, served.etag())) {
      throw new ApiException(
          ErrorCode.CONTENT_CHANGED_DURING_RESUME,
          "The package changed since the partial download began; discard it and re-plan.");
    }

    return ResponseEntity.ok()
        .eTag(served.etag())
        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
        .header(HttpHeaders.CONTENT_ENCODING, "identity")
        .contentType(JSON_UTF8)
        .body(new ByteArrayResource(served.bytes()));
  }

  /**
   * An identifier that is not a UUID at all reports the same per-type 404 as one that simply does
   * not exist. No manifest ever advertised it either way, and §4.4 gives exactly one answer for
   * "the entity does not exist".
   */
  private static UUID parseEntityId(ContentEntityType type, String entityId) {
    try {
      return UUID.fromString(entityId);
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          type.notFoundCode(),
          "No published %s package exists for that id.".formatted(type.name()));
    }
  }
}
