package dev.devatlas.server.content.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /admin/blog/posts/{id}} (§5.5.2). Every content field is optional -- {@code null}
 * means "leave unchanged" -- except {@code version} (§2.6). {@code status} and {@code source} are
 * not present at all: transitions use the lifecycle endpoints, and {@code source} is immutable.
 *
 * <p>{@code sourceUrl} is accepted here for {@code MANUAL} posts; on an {@code AUTO} post any
 * attempt to change it -- including to the same-looking value from a client that does not know it
 * is immutable -- is rejected by the service with {@code 403 AUTO_POST_NOT_EDITABLE} (§5.5.1 rule
 * 7), for every role.
 */
public record UpdateBlogPostRequest(
    @Size(min = 3, max = 80) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
    @Size(max = 200) String title,
    @Size(max = 200000) String bodyMarkdown,
    @Size(max = 2000) String sourceUrl,
    @NotNull Long version) {}
