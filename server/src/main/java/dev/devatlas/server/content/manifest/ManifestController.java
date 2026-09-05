package dev.devatlas.server.content.manifest;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two manifest endpoints of the content sync protocol (§4.1, §4.2).
 *
 * <p>Anonymous, like every endpoint under {@code /api/v1/manifest} and {@code /api/v1/content}
 * (§3.7 of the REST contract). The reasoning is not that manifests are uninteresting but that the
 * session must live in exactly one place: refresh tokens are single-use and rotated, so a download
 * engine holding one alongside the interface layer would trip reuse detection and sign the user out
 * mid-download. Abuse is bounded by the per-IP rate limit instead.
 *
 * <p>Responses are the canonical bytes the service produced, written straight to the wire. The
 * controller never re-serializes a manifest, because the {@code ETag} it returns was computed over
 * those exact bytes and a second serialization is a second chance for them to differ.
 */
@RestController
@RequestMapping("/api/v1/manifest")
public class ManifestController {

  private static final MediaType JSON_UTF8 =
      new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);

  private final ManifestService service;

  public ManifestController(ManifestService service) {
    this.service = service;
  }

  @GetMapping("/catalog")
  public ResponseEntity<byte[]> catalog(
      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
    return respond(service.catalog(), ifNoneMatch);
  }

  @GetMapping("/track/{trackId}")
  public ResponseEntity<byte[]> trackManifest(
      @PathVariable UUID trackId,
      @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
    return respond(service.trackManifest(trackId), ifNoneMatch);
  }

  /**
   * {@code Cache-Control: no-cache}, never {@code immutable}: unlike a package, a manifest at a
   * given URL changes. The client is expected to revalidate every time, and {@code If-None-Match}
   * is what makes that revalidation cost a header exchange instead of a download.
   */
  private static ResponseEntity<byte[]> respond(ManifestDocument manifest, String ifNoneMatch) {
    if (ConditionalRequests.matches(ifNoneMatch, manifest.etag())) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
          .eTag(manifest.etag())
          .header(HttpHeaders.CACHE_CONTROL, "no-cache")
          .build();
    }
    return ResponseEntity.ok()
        .eTag(manifest.etag())
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .contentType(JSON_UTF8)
        .body(manifest.bytes());
  }
}
