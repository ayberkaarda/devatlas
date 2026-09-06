package dev.bytelore.server.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link UserLocale} as its lowercase two-letter code.
 *
 * <p>{@code @Enumerated(STRING)} would store the constant name, {@code EN}, which the column's
 * check constraint rejects and which would disagree with the value the API emits. One
 * representation, everywhere.
 */
@Converter
public class UserLocaleConverter implements AttributeConverter<UserLocale, String> {

  @Override
  public String convertToDatabaseColumn(UserLocale attribute) {
    return attribute == null ? null : attribute.code();
  }

  @Override
  public UserLocale convertToEntityAttribute(String dbData) {
    return UserLocale.fromCode(dbData);
  }
}
