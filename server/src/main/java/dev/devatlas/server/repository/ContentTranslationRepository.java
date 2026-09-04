package dev.devatlas.server.repository;

import dev.devatlas.server.domain.ContentTranslation;
import dev.devatlas.server.domain.TranslationEntityType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link ContentTranslation}. */
public interface ContentTranslationRepository extends JpaRepository<ContentTranslation, UUID> {

  List<ContentTranslation> findByEntityTypeAndEntityId(
      TranslationEntityType entityType, UUID entityId);

  Optional<ContentTranslation> findByEntityTypeAndEntityIdAndLocale(
      TranslationEntityType entityType, UUID entityId, String locale);
}
