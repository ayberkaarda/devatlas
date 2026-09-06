package dev.devatlas.server.content.admin;

import dev.devatlas.server.common.ApiException;
import dev.devatlas.server.common.ApiFieldError;
import dev.devatlas.server.common.ErrorCode;
import dev.devatlas.server.common.MarkdownSanitizer;
import dev.devatlas.server.common.PageQuery;
import dev.devatlas.server.common.PageResponse;
import dev.devatlas.server.common.TextNormalizer;
import dev.devatlas.server.common.UuidV7;
import dev.devatlas.server.content.admin.dto.AdminBlogPostResponse;
import dev.devatlas.server.content.admin.dto.AuditLogItemResponse;
import dev.devatlas.server.content.admin.dto.BlogTransitionRequest;
import dev.devatlas.server.content.admin.dto.CreateBlogPostRequest;
import dev.devatlas.server.content.admin.dto.UpdateBlogPostRequest;
import dev.devatlas.server.domain.BlogPost;
import dev.devatlas.server.domain.BlogSource;
import dev.devatlas.server.domain.BlogStatus;
import dev.devatlas.server.domain.PipelineAuditLog;
import dev.devatlas.server.domain.PipelineStep;
import dev.devatlas.server.domain.Role;
import dev.devatlas.server.domain.SourceUpdate;
import dev.devatlas.server.domain.VerifyStatus;
import dev.devatlas.server.pipeline.PipelineAuditor;
import dev.devatlas.server.repository.BlogPostRepository;
import dev.devatlas.server.repository.PipelineAuditLogRepository;
import dev.devatlas.server.repository.SourceUpdateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authoring and lifecycle for {@link BlogPost} (§5.5).
 *
 * <p>This is also where the ingest pipeline creates and auto-submits its drafts ({@link
 * #createAutoDraft} and {@link #submitAutoDraft}), rather than the pipeline touching the {@code
 * blog_posts} table itself: {@link #doSubmit} is the single implementation of the {@code DRAFT ->
 * PENDING_REVIEW} transition, shared by a human clicking "submit" and by the pipeline finishing a
 * draft, and {@link #publish} is the single implementation that refuses an {@code AUTO} post
 * unconditionally. There is exactly one method in this codebase that can move a post to {@code
 * PUBLISHED} without an {@code ADMIN} caller -- {@link #publish}, for {@code MANUAL} posts only --
 * and it is written to make an {@code AUTO} post take that path structurally impossible, not merely
 * unlikely.
 */
@Service
public class AdminBlogPostService {

  private static final Map<String, String> SORT_FIELDS =
      Map.of(
          "created_at", "createdAt",
          "updated_at", "updatedAt",
          "published_at", "publishedAt",
          "title", "title");
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

  private final BlogPostRepository blogPosts;
  private final SourceUpdateRepository sourceUpdates;
  private final PipelineAuditLogRepository auditLogs;
  private final PipelineAuditor auditor;
  private final Clock clock;

  public AdminBlogPostService(
      BlogPostRepository blogPosts,
      SourceUpdateRepository sourceUpdates,
      PipelineAuditLogRepository auditLogs,
      PipelineAuditor auditor,
      Clock clock) {
    this.blogPosts = blogPosts;
    this.sourceUpdates = sourceUpdates;
    this.auditLogs = auditLogs;
    this.auditor = auditor;
    this.clock = clock;
  }

  @Transactional
  public AdminBlogPostResponse create(CreateBlogPostRequest request, UUID actorUserId) {
    if (blogPosts.existsBySlug(request.slug())) {
      throw new ApiException(
          ErrorCode.SLUG_ALREADY_EXISTS,
          "A blog post already exists with slug '%s'.".formatted(request.slug()));
    }

    requireValidSourceUrlIfPresent(request.sourceUrl());

    Instant now = now();
    BlogPost post = new BlogPost();
    post.setId(UuidV7.randomUuid());
    post.setSlug(request.slug());
    post.setTitle(TextNormalizer.normalize(request.title()));
    post.setBodyMarkdown(sanitizeBody(request.bodyMarkdown()));
    post.setStatus(BlogStatus.DRAFT);
    post.setSource(BlogSource.MANUAL);
    post.setSourceUrl(request.sourceUrl());
    post.setSourceUpdateId(null);
    post.setCreatedBy(actorUserId);
    post.setCreatedAt(now);
    post.setUpdatedAt(now);
    BlogPost saved = blogPosts.save(post);
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public PageResponse<AdminBlogPostResponse> list(
      Integer page, Integer size, List<String> sort, String status, String source, String query) {
    Pageable pageable = PageQuery.resolve(page, size, sort, SORT_FIELDS, DEFAULT_SORT);
    BlogStatus statusFilter = parseStatus(status);
    BlogSource sourceFilter = parseSource(source);
    String trimmedQuery = (query == null || query.isBlank()) ? null : query.trim();

    Page<BlogPost> result = blogPosts.search(statusFilter, sourceFilter, trimmedQuery, pageable);
    List<AdminBlogPostResponse> items = result.getContent().stream().map(this::toResponse).toList();
    return PageResponse.of(items, result.getNumber(), result.getSize(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public AdminBlogPostResponse get(UUID id) {
    return toResponse(requireBlogPost(id));
  }

  @Transactional
  public AdminBlogPostResponse update(UUID id, UpdateBlogPostRequest request, Role callerRole) {
    BlogPost post = requireBlogPost(id);
    requireVersion(post.getVersion(), request.version());

    boolean editsSourceUrl =
        request.sourceUrl() != null && !Objects.equals(request.sourceUrl(), post.getSourceUrl());
    boolean editsBody =
        (request.slug() != null && !request.slug().equals(post.getSlug()))
            || request.title() != null
            || request.bodyMarkdown() != null;

    if (post.getSource() == BlogSource.AUTO) {
      // Rule 7 (§5.5.1): the source link of an AUTO post is immutable for every role, ADMIN
      // included. It is the provenance of automatically generated text, and repointing it would
      // make the audit trail something that itself needs auditing.
      if (editsSourceUrl) {
        throw new ApiException(
            ErrorCode.AUTO_POST_NOT_EDITABLE,
            "The source link of an automatically sourced post cannot be changed by any role.");
      }
      // An EDITOR may read and submit an AUTO post but may not edit its body; an ADMIN may.
      if (editsBody && callerRole == Role.EDITOR) {
        throw new ApiException(
            ErrorCode.AUTO_POST_NOT_EDITABLE,
            "An editor may not edit the body of an automatically sourced post.");
      }
    }

    boolean changed = false;
    if (request.slug() != null && !request.slug().equals(post.getSlug())) {
      if (blogPosts.existsBySlugAndIdNot(request.slug(), id)) {
        throw new ApiException(
            ErrorCode.SLUG_ALREADY_EXISTS,
            "A blog post already exists with slug '%s'.".formatted(request.slug()));
      }
      post.setSlug(request.slug());
      changed = true;
    }
    if (request.title() != null) {
      post.setTitle(TextNormalizer.normalize(request.title()));
      changed = true;
    }
    if (request.bodyMarkdown() != null) {
      post.setBodyMarkdown(sanitizeBody(request.bodyMarkdown()));
      changed = true;
    }
    if (request.sourceUrl() != null && post.getSource() == BlogSource.MANUAL) {
      requireValidSourceUrlIfPresent(request.sourceUrl());
      post.setSourceUrl(request.sourceUrl());
      changed = true;
    }

    if (changed) {
      post.setUpdatedAt(now());
    }
    BlogPost saved = blogPosts.save(post);
    return toResponse(saved);
  }

  @Transactional
  public void delete(UUID id) {
    BlogPost post = requireBlogPost(id);
    if (post.getStatus() == BlogStatus.PUBLISHED) {
      throw new ApiException(
          ErrorCode.PUBLISHED_DELETE_BLOCKED,
          "A published post cannot be deleted; unpublish it first.");
    }
    // The audit log has no ON DELETE CASCADE from blog_posts on purpose (it is append-only and
    // nothing else writes to it); once the post itself is gone there is no provenance left for its
    // trail to describe, so the trail is removed with it here, explicitly, rather than leaving a
    // foreign key violation for the caller to puzzle out.
    auditLogs.deleteByBlogPostId(id);
    blogPosts.delete(post);
  }

  @Transactional
  public AdminBlogPostResponse submit(UUID id, BlogTransitionRequest request, UUID actorUserId) {
    BlogPost post = requireBlogPost(id);
    requireTransition(
        post, Set.of(BlogStatus.DRAFT, BlogStatus.REJECTED), request.expectedStatus());
    doSubmit(post, actorUserId, request.reason());
    return toResponse(post);
  }

  @Transactional
  public AdminBlogPostResponse approve(UUID id, BlogTransitionRequest request, UUID actorUserId) {
    BlogPost post = requireBlogPost(id);
    requireTransition(post, Set.of(BlogStatus.PENDING_REVIEW), request.expectedStatus());

    if (post.getSource() == BlogSource.AUTO) {
      SourceUpdate sourceUpdate = requireSourceUpdate(post.getSourceUpdateId());
      if (sourceUpdate.getVerifyStatus() != VerifyStatus.VERIFIED) {
        throw new ApiException(
            ErrorCode.SOURCE_UPDATE_NOT_VERIFIED,
            "The source update behind this post did not pass verification and cannot be approved.");
      }
    }

    BlogStatus from = post.getStatus();
    post.setStatus(BlogStatus.PUBLISHED);
    post.setPublishedAt(now());
    post.setUpdatedAt(now());
    blogPosts.save(post);
    auditor.record(
        PipelineStep.APPROVE,
        post.getId(),
        post.getSourceUpdateId(),
        null,
        actorUserId,
        from,
        BlogStatus.PUBLISHED,
        request.reason());
    return toResponse(post);
  }

  @Transactional
  public AdminBlogPostResponse reject(UUID id, BlogTransitionRequest request, UUID actorUserId) {
    requireReason(request.reason());
    BlogPost post = requireBlogPost(id);
    requireTransition(post, Set.of(BlogStatus.PENDING_REVIEW), request.expectedStatus());

    BlogStatus from = post.getStatus();
    post.setStatus(BlogStatus.REJECTED);
    post.setUpdatedAt(now());
    blogPosts.save(post);
    auditor.record(
        PipelineStep.REJECT,
        post.getId(),
        post.getSourceUpdateId(),
        null,
        actorUserId,
        from,
        BlogStatus.REJECTED,
        request.reason());
    return toResponse(post);
  }

  /**
   * {@code DRAFT}/{@code PENDING_REVIEW -> PUBLISHED}, manual posts only.
   *
   * <p>The {@code source == AUTO} check is the first thing this method does, unconditionally,
   * before the expected-status check, before anything else -- this is the one place in the whole
   * codebase "the rule this phase exists to enforce" is a single {@code if}. There is no parameter
   * that weakens it and no caller, {@code ADMIN} included, that can be routed around it: an
   * automatically sourced post always fails here with {@code 409 AUTO_POST_APPROVAL_REQUIRED} and
   * can only ever reach {@code PUBLISHED} through {@link #approve}.
   */
  @Transactional
  public AdminBlogPostResponse publish(UUID id, BlogTransitionRequest request, UUID actorUserId) {
    BlogPost post = requireBlogPost(id);
    if (post.getSource() == BlogSource.AUTO) {
      throw new ApiException(
          ErrorCode.AUTO_POST_APPROVAL_REQUIRED,
          "An automatically sourced post can only be published through admin approval.");
    }
    requireTransition(
        post, Set.of(BlogStatus.DRAFT, BlogStatus.PENDING_REVIEW), request.expectedStatus());

    BlogStatus from = post.getStatus();
    post.setStatus(BlogStatus.PUBLISHED);
    post.setPublishedAt(now());
    post.setUpdatedAt(now());
    blogPosts.save(post);
    auditor.record(
        PipelineStep.PUBLISH,
        post.getId(),
        post.getSourceUpdateId(),
        null,
        actorUserId,
        from,
        BlogStatus.PUBLISHED,
        request.reason());
    return toResponse(post);
  }

  @Transactional
  public AdminBlogPostResponse unpublish(UUID id, BlogTransitionRequest request, UUID actorUserId) {
    requireReason(request.reason());
    BlogPost post = requireBlogPost(id);
    requireTransition(post, Set.of(BlogStatus.PUBLISHED), request.expectedStatus());

    BlogStatus from = post.getStatus();
    post.setStatus(BlogStatus.DRAFT);
    post.setPublishedAt(null);
    post.setUpdatedAt(now());
    blogPosts.save(post);
    auditor.record(
        PipelineStep.UNPUBLISH,
        post.getId(),
        post.getSourceUpdateId(),
        null,
        actorUserId,
        from,
        BlogStatus.DRAFT,
        request.reason());
    return toResponse(post);
  }

  @Transactional(readOnly = true)
  public PageResponse<AuditLogItemResponse> auditLog(UUID id, Integer page, Integer size) {
    requireBlogPost(id);
    Pageable pageable =
        PageQuery.resolve(page, size, null, Map.of(), Sort.by(Sort.Direction.ASC, "occurredAt"));
    Page<PipelineAuditLog> result = auditLogs.findByBlogPostId(id, pageable);
    List<AuditLogItemResponse> items =
        result.getContent().stream()
            .map(
                log ->
                    new AuditLogItemResponse(
                        log.getId(),
                        log.getStep(),
                        log.getActorUserId(),
                        log.getFromStatus(),
                        log.getToStatus(),
                        log.getReason(),
                        log.getOccurredAt()))
            .toList();
    return PageResponse.of(items, result.getNumber(), result.getSize(), result.getTotalElements());
  }

  // ---- Pipeline entry points -----------------------------------------------------------------

  /**
   * Creates an {@code AUTO} draft from a verified source update. Called only by the ingest pipeline
   * -- there is no controller path that lets a client set {@code source = AUTO}.
   */
  @Transactional
  public BlogPost createAutoDraft(
      String slugBase,
      String title,
      String bodyMarkdown,
      String sourceUrl,
      UUID sourceUpdateId,
      UUID actorUserId) {
    String slug = uniqueSlug(slugBase);

    Instant now = now();
    BlogPost post = new BlogPost();
    post.setId(UuidV7.randomUuid());
    post.setSlug(slug);
    post.setTitle(TextNormalizer.normalize(title));
    post.setBodyMarkdown(sanitizeBody(bodyMarkdown));
    post.setStatus(BlogStatus.DRAFT);
    post.setSource(BlogSource.AUTO);
    post.setSourceUrl(sourceUrl);
    post.setSourceUpdateId(sourceUpdateId);
    post.setCreatedBy(null);
    post.setCreatedAt(now);
    post.setUpdatedAt(now);
    BlogPost saved = blogPosts.save(post);

    auditor.record(
        PipelineStep.DRAFT,
        saved.getId(),
        sourceUpdateId,
        null,
        actorUserId,
        null,
        BlogStatus.DRAFT,
        "Drafted from source update %s.".formatted(sourceUpdateId));
    return saved;
  }

  /**
   * Auto-submits a just-created {@code AUTO} draft for review, through the same code path a human
   * "submit" click uses.
   */
  @Transactional
  public BlogPost submitAutoDraft(BlogPost post, UUID actorUserId) {
    doSubmit(post, actorUserId, null);
    return post;
  }

  // ---- Internals ------------------------------------------------------------------------------

  private void doSubmit(BlogPost post, UUID actorUserId, String reason) {
    if (post.getSource() == BlogSource.AUTO
        && (post.getSourceUrl() == null || post.getSourceUrl().isBlank())) {
      throw new ApiException(
          ErrorCode.AUTO_POST_SOURCE_LINK_REQUIRED,
          "An automatically sourced post has no source link and cannot leave DRAFT.");
    }
    BlogStatus from = post.getStatus();
    post.setStatus(BlogStatus.PENDING_REVIEW);
    post.setUpdatedAt(now());
    blogPosts.save(post);
    auditor.record(
        PipelineStep.SUBMIT,
        post.getId(),
        post.getSourceUpdateId(),
        null,
        actorUserId,
        from,
        BlogStatus.PENDING_REVIEW,
        reason);
  }

  private String uniqueSlug(String base) {
    String candidate = base;
    int attempt = 0;
    while (blogPosts.existsBySlug(candidate)) {
      attempt++;
      String suffix = "-" + UUID.randomUUID().toString().substring(0, 6);
      int maxBaseLength = 80 - suffix.length();
      String truncatedBase =
          base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;
      candidate = truncatedBase + suffix;
      if (attempt > 10) {
        throw new ApiException(
            ErrorCode.SLUG_ALREADY_EXISTS,
            "Could not derive a unique slug from '%s'.".formatted(base));
      }
    }
    return candidate;
  }

  private String sanitizeBody(String rawBody) {
    String normalized = TextNormalizer.normalize(rawBody);
    String sanitized = MarkdownSanitizer.sanitizeMarkdown(normalized);
    if (sanitized == null || sanitized.isBlank()) {
      throw new ApiException(
          ErrorCode.SANITIZED_CONTENT_EMPTY, "Sanitizing the body left no content to store.");
    }
    return sanitized;
  }

  private void requireTransition(BlogPost post, Set<BlogStatus> allowedFrom, BlogStatus expected) {
    if (post.getStatus() != expected || !allowedFrom.contains(post.getStatus())) {
      throw new ApiException(
          ErrorCode.INVALID_STATE_TRANSITION,
          "Post '%s' is '%s', not '%s', or this transition is not legal from that status."
              .formatted(post.getId(), post.getStatus(), expected));
    }
  }

  /**
   * A blank or absent {@code source_url} is fine on a {@code MANUAL} post -- the source link isn't
   * required outside the pipeline -- but a value that is present has to be an absolute {@code
   * https://} URL, the same rule already applied to a whitelist source's own URLs, so a relative
   * path, a plain {@code http://} link or a {@code javascript:} value can never be stored as
   * provenance for a post.
   */
  private static void requireValidSourceUrlIfPresent(String sourceUrl) {
    if (sourceUrl == null || sourceUrl.isBlank()) {
      return;
    }
    if (!sourceUrl.startsWith("https://")) {
      throw new ApiException(
          ErrorCode.INSECURE_SOURCE_URL,
          "'source_url' must be an absolute https:// URL when provided.");
    }
  }

  private static void requireReason(String reason) {
    if (reason == null || reason.trim().length() < 10) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "'reason' is required and must be at least 10 characters for this transition.",
          List.of(new ApiFieldError("reason", "SIZE", "must be at least 10 characters")));
    }
  }

  private static BlogStatus parseStatus(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return BlogStatus.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          ErrorCode.INVALID_PARAMETER, "'status' has an unrecognised value '%s'.".formatted(value));
    }
  }

  private static BlogSource parseSource(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return BlogSource.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ApiException(
          ErrorCode.INVALID_PARAMETER, "'source' has an unrecognised value '%s'.".formatted(value));
    }
  }

  private AdminBlogPostResponse toResponse(BlogPost post) {
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

  private SourceUpdate requireSourceUpdate(UUID id) {
    return sourceUpdates
        .findById(id)
        .orElseThrow(
            () ->
                new ApiException(
                    ErrorCode.SOURCE_UPDATE_NOT_FOUND,
                    "No source update exists with id '%s'.".formatted(id)));
  }

  private static void requireVersion(long actual, long expected) {
    if (actual != expected) {
      throw new ApiException(
          ErrorCode.VERSION_CONFLICT,
          "The resource was modified concurrently; re-read it and retry.");
    }
  }

  private Instant now() {
    return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
  }
}
