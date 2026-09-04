package dev.devatlas.server.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * The four interface locales.
 *
 * <p>Unlike every other enum on this API, locale codes travel and are stored in their lowercase
 * two-letter form. The constant name is uppercase because Java constants are, and {@link #code()}
 * is the only representation that ever leaves the process -- on the wire, in the database, and in a
 * query parameter.
 */
public enum UserLocale {
  EN("en"),
  TR("tr"),
  FR("fr"),
  DE("de");

  private final String code;

  UserLocale(String code) {
    this.code = code;
  }

  @JsonValue
  public String code() {
    return code;
  }

  /**
   * Parses a locale code, case-insensitively.
   *
   * @return the matching locale, or {@code null} when the value is not one of the four supported
   *     codes. Callers decide which error that is; this method does not guess.
   */
  public static UserLocale fromCode(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    for (UserLocale locale : values()) {
      if (locale.code.equals(normalized)) {
        return locale;
      }
    }
    return null;
  }
}
