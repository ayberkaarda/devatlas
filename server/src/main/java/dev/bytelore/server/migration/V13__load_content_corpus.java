package dev.bytelore.server.migration;

import dev.bytelore.server.corpus.CorpusFiles;
import dev.bytelore.server.corpus.CorpusParser;
import dev.bytelore.server.corpus.CorpusWriter;
import dev.bytelore.server.corpus.ParsedCorpus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.SortedMap;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads the teaching corpus that lives in the repository as markdown.
 *
 * <p>A Java migration rather than SQL because the point of the corpus is that it crosses the same
 * write boundary authored content crosses. A body arriving through the administration API is
 * normalized and examined by static predicates before it is stored; a body arriving as an {@code
 * INSERT} statement is examined by nothing at all. Ninety lessons of escaped SQL would also be
 * unreviewable as prose, which matters more than it sounds: a lesson that teaches the wrong thing
 * renders perfectly and is indistinguishable from a correct one to every mechanism in this system,
 * so a person reading it is the only instrument that detects it.
 *
 * <p>A migration rather than a startup runner because the contents of a database should be a
 * function of its migration history. A loader that ran on every start would make what a deployment
 * holds depend on which build last started against it.
 *
 * <p><strong>The corpus location is configuration, not a constant.</strong> The default names the
 * directory that belongs to this migration version, and the test suite points it at a fixture
 * corpus instead. A loader with a hard-coded path could only ever be tested against the real
 * corpus, which means every refusal it is supposed to make would have to be provoked by breaking
 * the real corpus.
 *
 * <p>An absent corpus directory loads nothing and is not an error: the loader ships before the
 * content it will carry, and a migration that refused to run without it would stop the application
 * from starting for the whole time in between. A directory that exists but is malformed is an
 * entirely different matter and fails the migration.
 *
 * <p><strong>The checksum.</strong> {@link #getChecksum()} covers the corpus files, so editing a
 * loaded lesson afterwards invalidates the migration and is caught rather than silently diverging
 * from what a database already holds. It is a change detector over a directory -- paths sorted,
 * line endings folded, so a Windows checkout and a Linux one agree -- and not a content digest: no
 * value from it reaches a {@code sha256} column, and the loader leaves the package columns null for
 * the packaging step that owns them.
 */
@Component
public class V13__load_content_corpus extends BaseJavaMigration {

  private static final Logger log = LoggerFactory.getLogger(V13__load_content_corpus.class);

  private final ResourcePatternResolver resourceResolver;
  private final String corpusLocation;
  private final Clock clock;

  /** Read once: Flyway asks for the checksum and then migrates, and the two must see one corpus. */
  private SortedMap<String, byte[]> files;

  public V13__load_content_corpus(
      ResourcePatternResolver resourceResolver,
      @Value("${bytelore.corpus.location:classpath:content/v13/}") String corpusLocation,
      Clock clock) {
    this.resourceResolver = resourceResolver;
    this.corpusLocation = corpusLocation;
    this.clock = clock;
  }

  @Override
  public Integer getChecksum() {
    return CorpusFiles.checksum(corpus());
  }

  @Override
  public void migrate(Context context) throws Exception {
    SortedMap<String, byte[]> corpus = corpus();
    if (corpus.isEmpty()) {
      log.info("No corpus at '{}'; nothing to load.", corpusLocation);
      return;
    }

    ParsedCorpus parsed = CorpusParser.parse(corpus);
    Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    int tracks = new CorpusWriter(context.getConnection(), now).write(parsed);
    log.info("Loaded {} track(s) from the corpus at '{}'.", tracks, corpusLocation);
  }

  private synchronized SortedMap<String, byte[]> corpus() {
    if (files == null) {
      files = CorpusFiles.read(resourceResolver, corpusLocation);
    }
    return files;
  }
}
