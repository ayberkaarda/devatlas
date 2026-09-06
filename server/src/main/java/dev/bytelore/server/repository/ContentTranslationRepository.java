package dev.bytelore.server.repository;

import dev.bytelore.server.domain.ContentTranslation;
import dev.bytelore.server.domain.TranslationEntityType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link ContentTranslation}. */
public interface ContentTranslationRepository extends JpaRepository<ContentTranslation, UUID> {

  List<ContentTranslation> findByEntityTypeAndEntityId(
      TranslationEntityType entityType, UUID entityId);

  List<ContentTranslation> findByEntityTypeAndEntityIdIn(
      TranslationEntityType entityType, Collection<UUID> entityIds);

  Optional<ContentTranslation> findByEntityTypeAndEntityIdAndLocale(
      TranslationEntityType entityType, UUID entityId, String locale);
}
