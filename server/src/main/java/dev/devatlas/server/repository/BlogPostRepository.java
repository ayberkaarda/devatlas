package dev.devatlas.server.repository;

import dev.devatlas.server.domain.BlogPost;
import dev.devatlas.server.domain.BlogStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link BlogPost}. */
public interface BlogPostRepository extends JpaRepository<BlogPost, UUID> {

  Optional<BlogPost> findBySlug(String slug);

  Optional<BlogPost> findBySlugAndStatus(String slug, BlogStatus status);
}
