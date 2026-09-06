package dev.bytelore.server.content;

import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.common.PageQuery;
import dev.bytelore.server.common.PageResponse;
import dev.bytelore.server.content.dto.BlogPostDetailResponse;
import dev.bytelore.server.content.dto.BlogPostListItemResponse;
import dev.bytelore.server.domain.BlogPost;
import dev.bytelore.server.domain.BlogSource;
import dev.bytelore.server.domain.BlogStatus;
import dev.bytelore.server.domain.ContentTranslation;
import dev.bytelore.server.domain.TranslationEntityType;
import dev.bytelore.server.domain.UserLocale;
import dev.bytelore.server.repository.BlogPostRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only queries behind the two public blog endpoints of §5.2.5-6. Only {@code PUBLISHED}. */
@Service
public class PublicBlogService {

  private static final Map<String, String> SORT_FIELDS =
      Map.of("published_at", "publishedAt", "title", "title");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "publishedAt");

  private final BlogPostRepository posts;
  private final TranslationLookup translationLookup;

  public PublicBlogService(BlogPostRepository posts, TranslationLookup translationLookup) {
    this.posts = posts;
    this.translationLookup = translationLookup;
  }

  @Transactional(readOnly = true)
  public PageResponse<BlogPostListItemResponse> listPosts(
      Integer page, Integer size, List<String> sort, String sourceFilter, UserLocale requested) {
    Pageable pageable = PageQuery.resolve(page, size, sort, SORT_FIELDS, DEFAULT_SORT);

    Page<BlogPost> result;
    if (sourceFilter == null || sourceFilter.isBlank()) {
      result = posts.findByStatus(BlogStatus.PUBLISHED, pageable);
    } else {
      BlogSource source = parseSource(sourceFilter);
      result = posts.findByStatusAndSource(BlogStatus.PUBLISHED, source, pageable);
    }

    List<BlogPostListItemResponse> items =
        result.getContent().stream().map(post -> toListItem(post, requested)).toList();
    return PageResponse.of(items, result.getNumber(), result.getSize(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public BlogPostDetailResponse getPost(String slug, UserLocale requested) {
    BlogPost post =
        posts
            .findBySlugAndStatus(slug, BlogStatus.PUBLISHED)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.BLOG_POST_NOT_FOUND,
                        "No blog post exists with slug '%s'.".formatted(slug)));

    Optional<ContentTranslation> translation =
        translationLookup.find(TranslationEntityType.BLOG_POST, post.getId(), requested);
    TranslationFallback fallback = TranslationFallback.of(requested, translation.isPresent());

    return new BlogPostDetailResponse(
        post.getId(),
        post.getSlug(),
        translation.map(ContentTranslation::getTitle).orElse(post.getTitle()),
        translation.map(ContentTranslation::getBody).orElse(post.getBodyMarkdown()),
        post.getSource(),
        post.getSourceUrl(),
        post.getPublishedAt(),
        post.getUpdatedAt(),
        fallback.locale(),
        fallback.requestedLocale(),
        fallback.isFallback());
  }

  private BlogPostListItemResponse toListItem(BlogPost post, UserLocale requested) {
    Optional<ContentTranslation> translation =
        translationLookup.find(TranslationEntityType.BLOG_POST, post.getId(), requested);
    TranslationFallback fallback = TranslationFallback.of(requested, translation.isPresent());
    String body = translation.map(ContentTranslation::getBody).orElse(post.getBodyMarkdown());

    return new BlogPostListItemResponse(
        post.getId(),
        post.getSlug(),
        translation.map(ContentTranslation::getTitle).orElse(post.getTitle()),
        ExcerptGenerator.excerpt(body),
        post.getSource(),
        post.getSourceUrl(),
        post.getPublishedAt(),
        fallback.locale(),
        fallback.requestedLocale(),
        fallback.isFallback());
  }

  private static BlogSource parseSource(String value) {
    try {
      return BlogSource.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          ErrorCode.INVALID_PARAMETER,
          "'source' must be MANUAL or AUTO, got '%s'.".formatted(value));
    }
  }
}
