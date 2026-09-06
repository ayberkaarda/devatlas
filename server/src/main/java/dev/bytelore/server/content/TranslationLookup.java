package dev.bytelore.server.content;

import dev.bytelore.server.domain.ContentTranslation;
import dev.bytelore.server.domain.TranslationEntityType;
import dev.bytelore.server.domain.UserLocale;
import dev.bytelore.server.repository.ContentTranslationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Looks up the translation row (if any) that a requested locale resolves to. English never looks
 * anything up -- the canonical entity columns already are the English text (§2.7).
 */
@Component
public class TranslationLookup {

  private final ContentTranslationRepository translations;

  public TranslationLookup(ContentTranslationRepository translations) {
    this.translations = translations;
  }

  public Optional<ContentTranslation> find(
      TranslationEntityType entityType, UUID entityId, UserLocale requested) {
    if (requested == UserLocale.EN) {
      return Optional.empty();
    }
    return translations.findByEntityTypeAndEntityIdAndLocale(
        entityType, entityId, requested.code());
  }
}
