package dev.bytelore.server.common;

import java.text.Normalizer;

/**
 * The single place text is normalized before it is stored.
 *
 * <p>Normalization happens once, at the write boundary -- never at read time and never at the
 * moment a digest is computed. That ordering is the whole point: it makes the stored bytes, the
 * hashed bytes and the served bytes the same bytes. If normalization ran at hash time instead, the
 * stored string and the hashed string would be two different values and the server would have to
 * re-derive the second from the first on every read, which is exactly the "two implementations of
 * one rule" problem that a content digest exists to rule out.
 *
 * <p>Every method here is deliberately small and total. Nothing in it may become conditional on a
 * caller, a field or a locale: a normalization rule that varies is not a rule.
 */
public final class TextNormalizer {

  private static final char BYTE_ORDER_MARK = '\uFEFF';

  private TextNormalizer() {}

  /**
   * Normalizes prose: markdown bodies, titles, descriptions, captions and mind-map labels.
   *
   * <ol>
   *   <li>Strips a leading U+FEFF byte order mark.
   *   <li>Converts CRLF, then any remaining lone CR, to LF.
   *   <li>Applies Unicode Normalization Form C.
   * </ol>
   *
   * <p>Step 2 is not housekeeping. Text authored on Windows and text authored on Linux differ by
   * one byte per line for identical content, and a checkout can rewrite line endings in either
   * direction. Without it the same lesson digests differently depending on which machine last
   * touched it, and the mismatch surfaces only on the platform that did not produce the manifest.
   *
   * <p>Step 3 is a content-quality rule rather than a determinism requirement: an accented
   * character can be one code point or two, both render identically, and two authors on two
   * keyboards would otherwise produce entries that look the same and compare unequal.
   *
   * <p><strong>What this deliberately does not do:</strong> it does not strip trailing spaces and
   * it does not strip trailing newlines. Two trailing spaces are a hard line break in Markdown, and
   * removing them silently changes how a page renders. A digest that is stable because it quietly
   * corrupted the content is worse than no digest at all.
   *
   * @param value the text to normalize; {@code null} passes through unchanged
   * @return the normalized text, or {@code null} if {@code value} was {@code null}
   */
  public static String normalize(String value) {
    String normalized = normalizeLineEndings(value);
    if (normalized == null || normalized.isEmpty()) {
      return normalized;
    }
    return Normalizer.normalize(normalized, Normalizer.Form.NFC);
  }

  /**
   * Normalizes source code: strips a leading byte order mark and converts line endings, and stops
   * there.
   *
   * <p>Unicode composition is <strong>not</strong> applied to code. In prose an accented character
   * is the same character however it is composed; in a source listing an author's exact bytes are
   * the content, and silently recomposing them changes a string literal, an identifier or a test
   * fixture into something the author did not write.
   *
   * @param value the code to normalize; {@code null} passes through unchanged
   * @return the normalized code, or {@code null} if {@code value} was {@code null}
   */
  public static String normalizeCode(String value) {
    return normalizeLineEndings(value);
  }

  private static String normalizeLineEndings(String value) {
    if (value == null || value.isEmpty()) {
      return value;
    }
    String result = value;
    if (result.charAt(0) == BYTE_ORDER_MARK) {
      result = result.substring(1);
    }
    if (result.indexOf('\r') >= 0) {
      result = result.replace("\r\n", "\n").replace('\r', '\n');
    }
    return result;
  }
}
