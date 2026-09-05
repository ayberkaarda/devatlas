package dev.devatlas.server.pipeline;

import dev.devatlas.server.common.MarkdownSanitizer;
import dev.devatlas.server.common.TextNormalizer;
import dev.devatlas.server.common.UuidV7;
import dev.devatlas.server.content.admin.AdminBlogPostService;
import dev.devatlas.server.content.packaging.Sha256;
import dev.devatlas.server.domain.BlogPost;
import dev.devatlas.server.domain.PipelineStep;
import dev.devatlas.server.domain.SourceUpdate;
import dev.devatlas.server.domain.VerifyStatus;
import dev.devatlas.server.domain.WhitelistSource;
import dev.devatlas.server.repository.SourceUpdateRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the full "normalize, dedup, verify, draft" pipeline against one {@link FeedItem}, and writes
 * every step to {@link PipelineAuditor}.
 *
 * <p>A separate Spring bean (rather than a private method on {@link BlogIngestPipelineService}) so
 * that {@link #process} can be {@code @Transactional} without a self-invocation that would silently
 * skip Spring's proxy: each item is persisted in its own short transaction, after whatever network
 * calls the verification chain needed have already completed, so the pipeline never holds a
 * database connection open across an HTTP round trip.
 */
@Service
public class PipelineItemProcessor {

  private final SourceUpdateRepository sourceUpdates;
  private final VersionConfirmationService versionConfirmation;
  private final PipelineAuditor auditor;
  private final PipelineProperties properties;
  private final AdminBlogPostService blogPostService;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  public PipelineItemProcessor(
      SourceUpdateRepository sourceUpdates,
      VersionConfirmationService versionConfirmation,
      PipelineAuditor auditor,
      PipelineProperties properties,
      AdminBlogPostService blogPostService,
      JsonMapper jsonMapper,
      Clock clock) {
    this.sourceUpdates = sourceUpdates;
    this.versionConfirmation = versionConfirmation;
    this.auditor = auditor;
    this.properties = properties;
    this.blogPostService = blogPostService;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  @Transactional
  public ItemOutcome process(WhitelistSource source, FeedItem item, UUID actorUserId) {
    String rawContent = TextNormalizer.normalize(buildRawContent(item));
    String contentHash = Sha256.hex(rawContent.getBytes(StandardCharsets.UTF_8));

    Optional<SourceUpdate> existing =
        sourceUpdates.findByWhitelistSourceIdAndContentHash(source.getId(), contentHash);
    if (existing.isPresent()) {
      // Step 2 (dedup): a repeat of something already processed is dropped and audited, not
      // reprocessed -- no new row, no second network call.
      auditor.record(
          PipelineStep.NORMALIZE,
          null,
          existing.get().getId(),
          source.getId(),
          actorUserId,
          null,
          null,
          "Duplicate content_hash; already processed as source update %s."
              .formatted(existing.get().getId()));
      return ItemOutcome.duplicate();
    }

    Optional<String> versionOpt = VersionExtractor.extract(item);
    if (versionOpt.isEmpty()) {
      auditor.record(
          PipelineStep.VERIFY,
          null,
          null,
          source.getId(),
          actorUserId,
          null,
          null,
          "Rejected: no parseable version string in feed item '%s'.".formatted(item.title()));
      return ItemOutcome.rejected(
          new Rejection(
              null, VerifyCheckRecord.VERSION_CONFIRMED, "No version string could be extracted."));
    }
    String versionString = versionOpt.get();

    List<VerifyCheckRecord> checks = new ArrayList<>();
    String failedCheck = null;
    String failedDetail = null;

    boolean whitelisted = source.isEnabled();
    checks.add(
        new VerifyCheckRecord(
            VerifyCheckRecord.SOURCE_WHITELISTED,
            whitelisted,
            whitelisted ? "enabled=true" : "enabled=false"));
    if (!whitelisted) {
      failedCheck = VerifyCheckRecord.SOURCE_WHITELISTED;
      failedDetail = "enabled=false";
    }

    if (failedCheck == null) {
      VersionConfirmationService.VerifyOutcome verify =
          versionConfirmation.confirm(source.getVerifyUrlPattern(), versionString);
      checks.add(
          new VerifyCheckRecord(
              VerifyCheckRecord.VERSION_CONFIRMED, verify.passed(), verify.detail()));
      if (!verify.passed()) {
        failedCheck = VerifyCheckRecord.VERSION_CONFIRMED;
        failedDetail = verify.detail();
      }
    }

    if (failedCheck == null) {
      // The dedup lookup above already established this hash is new.
      checks.add(new VerifyCheckRecord(VerifyCheckRecord.HASH_NOT_SEEN, true, null));
    }

    if (failedCheck == null) {
      boolean hasSourceLink = item.link() != null && item.link().startsWith("https://");
      String sanitized = MarkdownSanitizer.sanitizeMarkdown(rawContent);
      boolean sane =
          hasSourceLink
              && rawContent.length() >= properties.getMinContentLength()
              && sanitized != null
              && !sanitized.isBlank();
      String detail =
          hasSourceLink
              ? "length=%d".formatted(rawContent.length())
              : "feed item has no usable https:// link for the mandatory source citation";
      checks.add(new VerifyCheckRecord(VerifyCheckRecord.CONTENT_SANITY, sane, detail));
      if (!sane) {
        failedCheck = VerifyCheckRecord.CONTENT_SANITY;
        failedDetail = detail;
      }
    }

    boolean verified = failedCheck == null;
    SourceUpdate sourceUpdate = new SourceUpdate();
    sourceUpdate.setId(UuidV7.randomUuid());
    sourceUpdate.setWhitelistSourceId(source.getId());
    sourceUpdate.setRawContent(rawContent);
    sourceUpdate.setVersionString(versionString);
    sourceUpdate.setContentHash(contentHash);
    sourceUpdate.setFetchedAt(now());
    sourceUpdate.setVerifyStatus(verified ? VerifyStatus.VERIFIED : VerifyStatus.REJECTED);
    sourceUpdate.setVerifyChecks(jsonMapper.writeValueAsString(checks));
    sourceUpdate.setCreatedAt(now());
    SourceUpdate saved = sourceUpdates.save(sourceUpdate);

    auditor.record(
        PipelineStep.VERIFY,
        null,
        saved.getId(),
        source.getId(),
        actorUserId,
        null,
        null,
        verified
            ? "Verification passed for version %s.".formatted(versionString)
            : "Rejected at %s: %s".formatted(failedCheck, failedDetail));

    if (!verified) {
      return ItemOutcome.rejected(new Rejection(versionString, failedCheck, failedDetail));
    }

    BlogDraftTemplate.DraftContent draft = BlogDraftTemplate.build(source, item, versionString);
    BlogPost post =
        blogPostService.createAutoDraft(
            draft.slugBase(),
            draft.title(),
            draft.bodyMarkdown(),
            draft.sourceUrl(),
            saved.getId(),
            actorUserId);
    // The pipeline never leaves a drafted post sitting at DRAFT: it immediately runs it through
    // the identical "submit" transition a human uses, so PENDING_REVIEW is reached exactly once,
    // through exactly one method, regardless of who -- or what -- triggered it.
    blogPostService.submitAutoDraft(post, actorUserId);

    return ItemOutcome.created(saved.getId());
  }

  private static String buildRawContent(FeedItem item) {
    String title = item.title() == null ? "" : item.title();
    String content = item.content() == null ? "" : item.content();
    return title + "\n\n" + content;
  }

  private Instant now() {
    return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
  }
}
