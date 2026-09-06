package dev.bytelore.server.content.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /admin/blog/posts} (§5.5.2). Always creates a {@code DRAFT} with {@code source =
 * MANUAL}; neither {@code status} nor {@code source} is accepted from the client.
 */
public record CreateBlogPostRequest(
    @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") String slug,
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 200000) String bodyMarkdown,
    @Size(max = 2000) String sourceUrl) {}
