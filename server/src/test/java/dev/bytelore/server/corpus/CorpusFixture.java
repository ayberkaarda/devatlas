package dev.bytelore.server.corpus;

import java.nio.charset.StandardCharsets;
import java.util.SortedMap;
import java.util.TreeMap;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The fixture corpus, and the two-line way to break one thing about it.
 *
 * <p>Every refusal test starts from the same corpus that loads cleanly and changes exactly one
 * value. That is what makes the result evidence: the load succeeded a moment ago with everything
 * else identical, so the refusal is attributable to the one thing that changed and to nothing else.
 * A hand-written broken corpus per case would prove far less, because a second thing wrong with it
 * would look the same from the outside.
 *
 * <p>The corpus is a plain map of path to bytes, which is also exactly what the loader consumes at
 * run time, so a mutation here goes through the same code the migration goes through.
 */
final class CorpusFixture {

  static final String LOCATION = "classpath:corpus/fixture/";

  private CorpusFixture() {}

  /** The fixture corpus as the migration would read it. */
  static SortedMap<String, byte[]> valid() {
    return CorpusFiles.read(new PathMatchingResourcePatternResolver(), LOCATION);
  }

  /** A mutable copy of the fixture, ready to have one thing changed about it. */
  static TreeMap<String, byte[]> copy() {
    return new TreeMap<>(valid());
  }

  /** The fixture with one substitution applied inside one file. */
  static TreeMap<String, byte[]> withReplacement(String path, String from, String to) {
    TreeMap<String, byte[]> corpus = copy();
    String text = new String(corpus.get(path), StandardCharsets.UTF_8);
    if (!text.contains(from)) {
      throw new IllegalArgumentException(
          "The fixture no longer contains '%s' in %s, so this test would prove nothing."
              .formatted(from, path));
    }
    corpus.put(path, text.replace(from, to).getBytes(StandardCharsets.UTF_8));
    return corpus;
  }

  /** The fixture with one file's content appended to. */
  static TreeMap<String, byte[]> withAppendedTo(String path, String suffix) {
    TreeMap<String, byte[]> corpus = copy();
    String text = new String(corpus.get(path), StandardCharsets.UTF_8);
    corpus.put(path, (text + suffix).getBytes(StandardCharsets.UTF_8));
    return corpus;
  }

  static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }
}
