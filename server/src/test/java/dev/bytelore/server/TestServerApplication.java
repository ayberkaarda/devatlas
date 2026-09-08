package dev.bytelore.server;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs the real server against a throwaway Postgres so the desktop client's live protocol tests
 * have something to talk to.
 *
 * <p>This is a harness, not a deployment. Nothing here is packaged, nothing here starts in
 * production, and no integration test uses this class: it exists for the one workflow step that
 * points the desktop engine at a real server instead of at a mock.
 */
public class TestServerApplication {

  public static void main(String[] args) {
    SpringApplication.from(ServerApplication::main)
        .with(TestcontainersConfiguration.class, PublishedTrackPrecondition.class)
        .run(args);
  }

  /**
   * Makes the sample track visible on the public read paths, for the length of this harness process
   * only.
   *
   * <p><strong>This is a test arranging its own precondition, not the product publishing
   * anything.</strong> In the product, publication is an editorial act: a person reads the content
   * and commits a migration that sets the flag. Nothing automatic may do it, and nothing here
   * changes that -- this class is only ever wired by the {@code main} above, so an ordinary test
   * run never sees it and the migration chain still ends with the sample track withdrawn.
   *
   * <p><strong>Why the live tests need any published track at all.</strong> They exercise the sync
   * protocol end to end -- the catalog, a track manifest, package digests, {@code Range} and {@code
   * If-Match} resume, superseded-version negotiation -- and every one of those paths is scoped to
   * published content, because an unpublished track is deliberately invisible to the catalog. With
   * nothing published the catalog is empty and all five tests stop on their first assertion,
   * reporting an absent server-side precondition rather than anything about the protocol. Which
   * content they fetch does not matter to them; that there is content to fetch does.
   *
   * <p><strong>Why this track, and why publishing it here is safe.</strong> A migration withdrew it
   * because the authored corpus rewrote its content into lessons nobody had reviewed, and approval
   * attaches to content rather than to a row. Under the profile this harness runs with, the corpus
   * loader reads a fixture corpus instead of the authored one, so the row still holds the short
   * sample lessons that were written to give the read endpoints something real to serve -- not the
   * rewrite the withdrawal was about. Publishing it here restores exactly the state the live tests
   * were written against, and puts no unreviewed content anywhere.
   *
   * <p>Smallest possible arrangement: one flag on one already-seeded row. It writes no content,
   * touches no other track -- the fixture corpus stays unpublished, so the catalog carries exactly
   * one entry -- and leaves {@code content_version} alone, since publishing a track does not change
   * what it says and a bumped counter would tell a client to re-download text it already has.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class PublishedTrackPrecondition {

    private static final Logger log = LoggerFactory.getLogger(PublishedTrackPrecondition.class);

    /**
     * The sample seed's track, addressed by the identifier that migration fixes as a literal so
     * that every environment names the same row.
     */
    private static final UUID SAMPLE_TRACK_ID =
        UUID.fromString("019205a0-1000-7000-8000-000000000001");

    @Bean
    ApplicationRunner publishSampleTrackForLiveProtocolTests(DataSource dataSource) {
      return (ApplicationArguments args) -> {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        List<Boolean> current =
            jdbc.queryForList(
                "SELECT published FROM tracks WHERE id = ?", Boolean.class, SAMPLE_TRACK_ID);
        if (current.isEmpty()) {
          // Loud rather than silent: without this row the catalog is empty and every live test
          // fails on an assertion that describes the protocol, far away from the actual cause.
          throw new IllegalStateException(
              "The sample track "
                  + SAMPLE_TRACK_ID
                  + " is missing, so there is nothing for the live protocol tests to read. The"
                  + " seed migration that inserts it must have stopped running.");
        }
        if (Boolean.TRUE.equals(current.getFirst())) {
          return;
        }
        jdbc.update("UPDATE tracks SET published = true WHERE id = ?", SAMPLE_TRACK_ID);
        log.info(
            "Harness precondition: published the sample track {} so the live protocol tests have a"
                + " catalog entry to read. This process only.",
            SAMPLE_TRACK_ID);
      };
    }
  }
}
