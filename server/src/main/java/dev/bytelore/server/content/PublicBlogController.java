package dev.bytelore.server.content;

import dev.bytelore.server.common.PageResponse;
import dev.bytelore.server.content.dto.BlogPostDetailResponse;
import dev.bytelore.server.content.dto.BlogPostListItemResponse;
import dev.bytelore.server.domain.UserLocale;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The public blog read surface (§5.2.5-6): published posts only, anonymous. */
@RestController
public class PublicBlogController {

  private final PublicBlogService service;
  private final RequestLocaleResolver localeResolver;

  public PublicBlogController(PublicBlogService service, RequestLocaleResolver localeResolver) {
    this.service = service;
    this.localeResolver = localeResolver;
  }

  @GetMapping("/api/v1/blog/posts")
  public ResponseEntity<PageResponse<BlogPostListItemResponse>> listPosts(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) List<String> sort,
      @RequestParam(required = false) String source,
      @RequestParam(required = false) String locale,
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
    UserLocale requested = localeResolver.resolve(locale, acceptLanguage);
    PageResponse<BlogPostListItemResponse> body =
        service.listPosts(page, size, sort, source, requested);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_LANGUAGE, "en")
        .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
        .body(body);
  }

  @GetMapping("/api/v1/blog/posts/{slug}")
  public ResponseEntity<BlogPostDetailResponse> getPost(
      @PathVariable String slug,
      @RequestParam(required = false) String locale,
      @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
    UserLocale requested = localeResolver.resolve(locale, acceptLanguage);
    BlogPostDetailResponse body = service.getPost(slug, requested);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_LANGUAGE, body.locale())
        .header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE)
        .body(body);
  }
}
