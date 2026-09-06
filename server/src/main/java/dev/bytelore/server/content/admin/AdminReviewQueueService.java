package dev.bytelore.server.content.admin;

import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.common.PageQuery;
import dev.bytelore.server.common.PageResponse;
import dev.bytelore.server.content.admin.dto.AdminBlogPostResponse;
import dev.bytelore.server.content.admin.dto.ReviewQueueDetailResponse;
import dev.bytelore.server.content.admin.dto.SourceUpdateDetailResponse;
import dev.bytelore.server.content.admin.dto.WhitelistSourceSummary;
import dev.bytelore.server.domain.BlogPost;
import dev.bytelore.server.domain.BlogSource;
import dev.bytelore.server.domain.BlogStatus;
import dev.bytelore.server.domain.SourceUpdate;
import dev.bytelore.server.domain.WhitelistSource;
import dev.bytelore.server.pipeline.VerifyCheckRecord;
import dev.bytelore.server.repository.BlogPostRepository;
import dev.bytelore.server.repository.SourceUpdateRepository;
import dev.bytelore.server.repository.WhitelistSourceRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * The review queue (§5.7): {@code PENDING_REVIEW} posts, oldest first, and the side-by-side detail
 * view an {@code ADMIN} uses to decide whether to approve or reject one.
 */
@Service
public class AdminReviewQueueService {

  private static final Map<String, String> SORT_FIELDS =
      Map.of(
          "created_at", "createdAt",
          "updated_at", "updatedAt");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "createdAt");

  private final BlogPostRepository blogPosts;
  private final SourceUpdateRepository sourceUpdates;
  private final WhitelistSourceRepository whitelistSources;
  private final JsonMapper jsonMapper;

  public AdminReviewQueueService(
      BlogPostRepository blogPosts,
      SourceUpdateRepository sourceUpdates,
      WhitelistSourceRepository whitelistSources,
      JsonMapper jsonMapper) {
    this.blogPosts = blogPosts;
    this.sourceUpdates = sourceUpdates;
    this.whitelistSources = whitelistSources;
    this.jsonMapper = jsonMapper;
  }

  @Transactional(readOnly = true)
  public PageResponse<AdminBlogPostResponse> list(
      String source, Integer page, Integer size, List<String> sort) {
    Pageable pageable = PageQuery.resolve(page, size, sort, SORT_FIELDS, DEFAULT_SORT);
    Page<BlogPost> result =
        (source == null || source.isBlank())
            ? blogPosts.findByStatus(BlogStatus.PENDING_REVIEW, pageable)
            : blogPosts.findByStatusAndSource(
                BlogStatus.PENDING_REVIEW, parseSource(source), pageable);
    List<AdminBlogPostResponse> items =
        result.getContent().stream().map(this::toPostResponse).toList();
    return PageResponse.of(items, result.getNumber(), result.getSize(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public ReviewQueueDetailResponse detail(UUID postId) {
    BlogPost post = requireBlogPost(postId);
    SourceUpdateDetailResponse sourceUpdate =
        post.getSourceUpdateId() == null ? null : toSourceUpdateResponse(post.getSourceUpdateId());
    return new ReviewQueueDetailResponse(toPostResponse(post), sourceUpdate);
  }

  SourceUpdateDetailResponse toSourceUpdateResponse(UUID sourceUpdateId) {
    SourceUpdate sourceUpdate =
        sourceUpdates
            .findById(sourceUpdateId)
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.SOURCE_UPDATE_NOT_FOUND,
                        "No source update exists with id '%s'.".formatted(sourceUpdateId)));
    WhitelistSource whitelistSource =
        whitelistSources
            .findById(sourceUpdate.getWhitelistSourceId())
            .orElseThrow(
                () ->
                    new ApiException(
                        ErrorCode.WHITELIST_SOURCE_NOT_FOUND,
                        "No whitelist source exists with id '%s'."
                            .formatted(sourceUpdate.getWhitelistSourceId())));
    return new SourceUpdateDetailResponse(
        sourceUpdate.getId(),
        new WhitelistSourceSummary(
            whitelistSource.getId(), whitelistSource.getName(), whitelistSource.getFeedUrl()),
        sourceUpdate.getVersionString(),
        sourceUpdate.getContentHash(),
        sourceUpdate.getFetchedAt(),
        sourceUpdate.getVerifyStatus(),
        parseChecks(sourceUpdate.getVerifyChecks()),
        sourceUpdate.getRawContent());
  }

  private List<VerifyCheckRecord> parseChecks(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    return List.of(jsonMapper.readValue(json, VerifyCheckRecord[].class));
  }

  private AdminBlogPostResponse toPostResponse(BlogPost post) {
    return new AdminBlogPostResponse(
        post.getId(),
        post.getSlug(),
        post.getTitle(),
        post.getBodyMarkdown(),
        post.getStatus(),
        post.getSource(),
        post.getSourceUrl(),
        post.getSourceUpdateId(),
        post.getPublishedAt(),
        post.getCreatedBy(),
        post.getCreatedAt(),
        post.getUpdatedAt(),
        post.getVersion());
  }

  private BlogPost requireBlogPost(UUID id) {
    return blogPosts
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.BLOG_POST_NOT_FOUND,
                    "No blog post exists with id '%s'.".formatted(id)));
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
