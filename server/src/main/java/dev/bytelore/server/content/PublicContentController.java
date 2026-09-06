package dev.bytelore.server.content;

import dev.bytelore.server.auth.AccessTokenClaims;
import dev.bytelore.server.common.PageResponse;
import dev.bytelore.server.content.dto.LessonDetailResponse;
import dev.bytelore.server.content.dto.MindMapResponse;
import dev.bytelore.server.content.dto.TrackDetailResponse;
import dev.bytelore.server.content.dto.TrackListItemResponse;
import dev.bytelore.server.domain.UserLocale;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public content read surface (§5.2): published tracks, a track's detail, a lesson, and a
 * track's mind map. Anonymous by definition -- see {@code SecurityConfig} -- but a caller who
 * happens to present a valid access token is still recognized, which is how the lesson endpoint can
 * attach the caller's own progress without a separate authenticated variant of the route.
 */
@RestController
public class PublicContentController {

  private final PublicContentService service;
  private final RequestLocaleResolver localeResolver;

  public PublicContentController(
      PublicContentService service, RequestLocaleResolver localeResolver) {
    this.service = service;
    this.localeResolver = localeResolver;
  }

  @GetMapping("/api/v1/tracks")
  public ResponseEntity<PageResponse<TrackListItemResponse>> listTracks(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) List<String> sort,
      @RequestParam(required = false) String locale,
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
    UserLocale requested = localeResolver.resolve(locale, acceptLanguage);
    PageResponse<TrackListItemResponse> body = service.listTracks(page, size, sort, requested);
    return contentLanguageResponse("en", body);
  }

  @GetMapping("/api/v1/tracks/{slug}")
  public ResponseEntity<TrackDetailResponse> getTrack(
      @PathVariable String slug,
      @RequestParam(required = false) String locale,
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
    UserLocale requested = localeResolver.resolve(locale, acceptLanguage);
    TrackDetailResponse body = service.getTrackDetail(slug, requested);
    return contentLanguageResponse(body.locale(), body);
  }

  @GetMapping("/api/v1/lessons/{slug}")
  public ResponseEntity<LessonDetailResponse> getLesson(
      @PathVariable String slug,
      @RequestParam(required = false) String locale,
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
      @AuthenticationPrincipal AccessTokenClaims caller) {
    UserLocale requested = localeResolver.resolve(locale, acceptLanguage);
    LessonDetailResponse body =
        service.getLesson(slug, requested, caller == null ? null : caller.userId());
    return contentLanguageResponse(body.locale(), body);
  }

  @GetMapping("/api/v1/tracks/{slug}/mindmap")
  public ResponseEntity<MindMapResponse> getMindMap(
      @PathVariable String slug,
      @RequestParam(required = false) String locale,
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
    UserLocale requested = localeResolver.resolve(locale, acceptLanguage);
    MindMapResponse body = service.getMindMap(slug, requested);
    return contentLanguageResponse(body.locale(), body);
  }

  /**
   * Every content read endpoint carries {@code Content-Language} for the locale actually served on
   * the root entity, and {@code Vary: Accept-Language} so a shared cache never serves one caller's
   * negotiated locale to another (§2.7).
   */
  private static <T> ResponseEntity<T> contentLanguageResponse(String locale, T body) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_LANGUAGE, locale)
        .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
        .body(body);
  }
}
