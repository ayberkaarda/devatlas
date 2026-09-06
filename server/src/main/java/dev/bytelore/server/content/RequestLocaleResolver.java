package dev.bytelore.server.content;

import dev.bytelore.server.common.ApiException;
import dev.bytelore.server.common.ErrorCode;
import dev.bytelore.server.domain.UserLocale;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Resolves the locale a content read is served in (§2.7): {@code ?locale=}, then {@code
 * Accept-Language} parsed with q-values, then English.
 */
@Component
public class RequestLocaleResolver {

  /**
   * @param localeParam the {@code locale} query parameter, or {@code null}/blank if absent
   * @param acceptLanguageHeader the {@code Accept-Language} header, or {@code null}/blank if absent
   * @return the resolved locale; never {@code null}
   * @throws ApiException {@code UNSUPPORTED_LOCALE} if {@code localeParam} is present but is not
   *     one of {@code en|tr|fr|de}
   */
  public UserLocale resolve(String localeParam, String acceptLanguageHeader) {
    if (localeParam != null && !localeParam.isBlank()) {
      UserLocale locale = UserLocale.fromCode(localeParam);
      if (locale == null) {
        throw new ApiException(
            ErrorCode.UNSUPPORTED_LOCALE,
            "Locale '%s' is not one of en, tr, fr, de.".formatted(localeParam));
      }
      return locale;
    }
    if (acceptLanguageHeader != null && !acceptLanguageHeader.isBlank()) {
      UserLocale fromHeader = parseAcceptLanguage(acceptLanguageHeader);
      if (fromHeader != null) {
        return fromHeader;
      }
    }
    return UserLocale.EN;
  }

  /**
   * Parses q-value ranges and returns the first one whose primary language subtag matches a
   * supported locale ({@code tr-TR} matches {@code tr}). Returns {@code null} rather than throwing
   * when the header names nothing this API supports -- an unrecognized {@code Accept-Language}
   * falls back to English, it does not fail the request the way an invalid {@code ?locale=} does.
   */
  private UserLocale parseAcceptLanguage(String header) {
    List<Locale.LanguageRange> ranges;
    try {
      ranges = Locale.LanguageRange.parse(header);
    } catch (IllegalArgumentException malformed) {
      return null;
    }
    for (Locale.LanguageRange range : ranges) {
      String primary = range.getRange().split("-")[0];
      UserLocale locale = UserLocale.fromCode(primary);
      if (locale != null) {
        return locale;
      }
    }
    return null;
  }
}
