package dev.bytelore.server.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The corpus reader and the number Flyway compares between runs.
 *
 * <p>The checksum is the part with a real failure mode behind it. Flyway records it when the
 * migration runs and compares it on every later start, so a value that depended on the platform
 * would let a corpus load on a developer's machine and then refuse to start on a Linux runner --
 * with a message about a changed migration, pointing at a file nobody changed.
 */
class CorpusFilesTest {

  @Test
  void readsTheWholeTreeWithRelativeSlashSeparatedPaths() {
    SortedMap<String, byte[]> files = CorpusFixture.valid();

    assertThat(files).containsKey("fixture-path/track.json");
    assertThat(files).containsKey("fixture-path/lessons/what-a-value-holds.md");
    assertThat(files).containsKey("fixture-path/examples/what-a-value-holds-1.py");
    assertThat(files.keySet()).allSatisfy(path -> assertThat(path).doesNotContain("\\"));
  }

  /** An absent corpus is not an error: the loader ships before the content it will carry. */
  @Test
  void anAbsentLocationReadsAsAnEmptyCorpus() {
    SortedMap<String, byte[]> files =
        CorpusFiles.read(
            new PathMatchingResourcePatternResolver(), "classpath:corpus/no-such-corpus/");

    assertThat(files).isEmpty();
  }

  /**
   * The property the whole checksum exists for: two checkouts of one corpus that differ only in
   * line endings produce the same number.
   */
  @Test
  void theChecksumSurvivesACheckoutWithCarriageReturns() {
    TreeMap<String, byte[]> linux = CorpusFixture.copy();
    TreeMap<String, byte[]> windows = new TreeMap<>();
    linux.forEach(
        (path, content) ->
            windows.put(
                path,
                new String(content, StandardCharsets.UTF_8)
                    .replace("\n", "\r\n")
                    .getBytes(StandardCharsets.UTF_8)));

    assertThat(CorpusFiles.checksum(windows)).isEqualTo(CorpusFiles.checksum(linux));
  }

  /** An editor that saved one file with a byte order mark has not changed the corpus. */
  @Test
  void theChecksumIgnoresAByteOrderMark() {
    TreeMap<String, byte[]> withMark = CorpusFixture.copy();
    String path = "fixture-path/lessons/what-a-value-holds.md";
    withMark.put(
        path,
        ("﻿" + new String(withMark.get(path), StandardCharsets.UTF_8))
            .getBytes(StandardCharsets.UTF_8));

    assertThat(CorpusFiles.checksum(withMark))
        .isEqualTo(CorpusFiles.checksum(CorpusFixture.valid()));
  }

  /** Two readings of one corpus agree, whatever order the entries were put in. */
  @Test
  void theChecksumDoesNotDependOnInsertionOrder() {
    TreeMap<String, byte[]> forwards = new TreeMap<>();
    TreeMap<String, byte[]> backwards = new TreeMap<>();
    var entries = CorpusFixture.valid().entrySet().stream().toList();
    for (var entry : entries) {
      forwards.put(entry.getKey(), entry.getValue());
    }
    for (int i = entries.size() - 1; i >= 0; i--) {
      backwards.put(entries.get(i).getKey(), entries.get(i).getValue());
    }

    assertThat(CorpusFiles.checksum(backwards)).isEqualTo(CorpusFiles.checksum(forwards));
  }

  /** And it does change when the corpus does, which is the point of recording it. */
  @Test
  void theChecksumChangesWhenAFileChanges() {
    TreeMap<String, byte[]> edited =
        CorpusFixture.withAppendedTo("fixture-path/lessons/what-a-value-holds.md", "\nOne word.\n");

    assertThat(CorpusFiles.checksum(edited))
        .isNotEqualTo(CorpusFiles.checksum(CorpusFixture.valid()));
  }

  /** Moving a file changes it too: paths are folded in, not only contents. */
  @Test
  void theChecksumChangesWhenAFileMoves() {
    TreeMap<String, byte[]> moved = CorpusFixture.copy();
    byte[] content = moved.remove("fixture-path/examples/what-a-value-holds-1.py");
    moved.put("fixture-path/examples/what-a-value-holds-9.py", content);

    assertThat(CorpusFiles.checksum(moved))
        .isNotEqualTo(CorpusFiles.checksum(CorpusFixture.valid()));
  }

  @Test
  void normalizationFoldsEveryLineEndingAndDropsTheByteOrderMark() {
    byte[] normalized =
        CorpusFiles.normalizeBytes("﻿a\r\nb\rc\nd".getBytes(StandardCharsets.UTF_8));

    assertThat(new String(normalized, StandardCharsets.UTF_8)).isEqualTo("a\nb\nc\nd");
  }
}
