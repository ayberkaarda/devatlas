package dev.devatlas.server.domain;

/**
 * Kind of entity a {@link ContentTranslation} row translates. Mind maps are not translatable and
 * have no constant here.
 */
public enum TranslationEntityType {
  TRACK,
  MODULE,
  LESSON,
  BLOG_POST
}
