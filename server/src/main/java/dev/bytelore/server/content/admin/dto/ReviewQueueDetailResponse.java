package dev.bytelore.server.content.admin.dto;

/**
 * {@code GET /admin/review-queue/{postId}} (§5.7): everything the side-by-side review screen needs
 * in one call. The server ships the generated draft, the raw fetched source and the verification
 * record; the screen computes the diff client-side.
 *
 * <p>{@code sourceUpdate} is {@code null} for a {@code MANUAL} post that reached the review queue
 * through an ordinary "submit" -- it has no pipeline provenance to show.
 */
public record ReviewQueueDetailResponse(
    AdminBlogPostResponse post, SourceUpdateDetailResponse sourceUpdate) {}
