package dev.bytelore.server.corpus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * Reads a corpus directory into memory and produces the change-detection number Flyway compares
 * between runs.
 *
 * <p>The whole tree is read at once into a sorted map of relative path to raw bytes, and everything
 * downstream -- parsing, validation, the checksum -- works on that map alone. That is what lets the
 * refusal tests take a real corpus, change one entry, and observe the loader refuse it, without a
 * second reader implementation that might not behave like the real one.
 *
 * <p><strong>Platform stability.</strong> Flyway stores this number when the migration runs and
 * compares it on every later start, so the same corpus read on a Windows developer machine and on a
 * Linux runner has to produce the same value or one of the two refuses to start. Three things make
 * that true, and all three are necessary:
 *
 * <ol>
 *   <li>Paths are relative and always {@code /}-separated, taken from the resource URL rather than
 *       from a {@link java.io.File}, so a backslash never reaches the computation.
 *   <li>Entries are folded in sorted path order, not in the order the file system or the jar
 *       happened to enumerate them. Directory enumeration order is not specified anywhere and
 *       differs between file systems.
 *   <li>Every file's bytes are put through the same line-ending normalization the content pipeline
 *       applies before storage -- a leading byte order mark dropped, {@code CRLF} and lone {@code
 *       CR} folded to {@code LF} -- so a checkout that produced {@code CRLF} and one that produced
 *       {@code LF} agree. Without it the number would depend on a git setting rather than on the
 *       corpus.
 * </ol>
 *
 * <p>The result is a CRC32 folded to an {@code int}, which is what Flyway's checksum field holds.
 * It is a change detector for a directory of files and nothing else: it is not a content digest, it
 * never reaches a {@code sha256} column, and the loader leaves those columns null for the packaging
 * step that owns them.
 */
public final class CorpusFiles {

  /**
   * What a corpus path may contain. Deliberately narrow: a space or a non-ASCII character in a file
   * name survives a local checkout but arrives percent-encoded in a resource URL, and the relative
   * path derived from it would then differ from the name on disk. Refusing such a name outright is
   * simpler than making the two agree, and no corpus needs one.
   */
  private static final Pattern SAFE_RELATIVE_PATH = Pattern.compile("[A-Za-z0-9._/-]+");

  private static final byte[] UTF8_BYTE_ORDER_MARK = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

  private CorpusFiles() {}

  /**
   * Reads every file under {@code location} into a map keyed by path relative to it.
   *
   * <p>An absent location is not an error: it means no corpus has been added for this version yet,
   * and the migration loads nothing. A location that exists but holds a malformed corpus is a
   * different matter entirely and fails in {@link CorpusParser}.
   *
   * @param resolver the resource resolver, which reads from a directory and from inside a packaged
   *     jar alike
   * @param location a Spring resource location, for example {@code classpath:content/v13/}
   * @return relative path to raw file bytes, sorted by path
   */
  public static SortedMap<String, byte[]> read(ResourcePatternResolver resolver, String location) {
    String base = location.endsWith("/") ? location : location + "/";
    try {
      Resource root = resolver.getResource(base);
      if (!root.exists()) {
        return Collections.emptySortedMap();
      }
      String rootUrl = root.getURL().toString();
      if (!rootUrl.endsWith("/")) {
        rootUrl = rootUrl + "/";
      }

      TreeMap<String, byte[]> files = new TreeMap<>();
      for (Resource resource : resolver.getResources(base + "**")) {
        if (!resource.isReadable()) {
          // A directory. Spring reports one as unreadable, which is the only portable way to tell
          // the two apart across a file system and a jar.
          continue;
        }
        String url = resource.getURL().toString();
        if (!url.startsWith(rootUrl)) {
          continue;
        }
        String relativePath = url.substring(rootUrl.length());
        if (relativePath.isEmpty() || relativePath.endsWith("/")) {
          continue;
        }
        if (!SAFE_RELATIVE_PATH.matcher(relativePath).matches()) {
          throw new CorpusException(
              "Corpus path '%s' contains a character a corpus file name may not carry; use only"
                      .formatted(relativePath)
                  + " letters, digits, '.', '_' and '-'.");
        }
        files.put(relativePath, resource.getContentAsByteArray());
      }
      return files;
    } catch (IOException e) {
      throw new CorpusException("Corpus at '%s' could not be read.".formatted(location), e);
    }
  }

  /**
   * Folds a corpus into the number Flyway stores as this migration's checksum.
   *
   * <p>Each entry contributes its relative path and its normalized bytes, separated by a zero byte
   * so that moving a character from the end of one path to the start of the next content cannot
   * produce the same stream.
   */
  public static int checksum(SortedMap<String, byte[]> files) {
    CRC32 crc = new CRC32();
    for (var entry : files.entrySet()) {
      crc.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
      crc.update(0);
      crc.update(normalizeBytes(entry.getValue()));
      crc.update(0);
    }
    return (int) crc.getValue();
  }

  /**
   * Drops a leading UTF-8 byte order mark and folds {@code CRLF} and lone {@code CR} to {@code LF},
   * at the byte level.
   *
   * <p>Byte level rather than after decoding, because this runs over every file in the corpus
   * including ones the loader never decodes, and because the transformation is identical either way
   * for UTF-8: none of the bytes involved can appear inside a multi-byte sequence.
   */
  static byte[] normalizeBytes(byte[] raw) {
    int start = startsWithByteOrderMark(raw) ? UTF8_BYTE_ORDER_MARK.length : 0;
    byte[] out = new byte[raw.length - start];
    int length = 0;
    for (int i = start; i < raw.length; i++) {
      byte current = raw[i];
      if (current == '\r') {
        out[length++] = '\n';
        if (i + 1 < raw.length && raw[i + 1] == '\n') {
          i++;
        }
        continue;
      }
      out[length++] = current;
    }
    byte[] trimmed = new byte[length];
    System.arraycopy(out, 0, trimmed, 0, length);
    return trimmed;
  }

  private static boolean startsWithByteOrderMark(byte[] raw) {
    if (raw.length < UTF8_BYTE_ORDER_MARK.length) {
      return false;
    }
    for (int i = 0; i < UTF8_BYTE_ORDER_MARK.length; i++) {
      if (raw[i] != UTF8_BYTE_ORDER_MARK[i]) {
        return false;
      }
    }
    return true;
  }

  /** Decodes a corpus file as UTF-8, with the byte order mark and line endings already folded. */
  public static String text(byte[] raw) {
    return new String(normalizeBytes(raw), StandardCharsets.UTF_8);
  }
}
