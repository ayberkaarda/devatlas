package dev.bytelore.server.content;

import dev.bytelore.server.domain.Lesson;
import dev.bytelore.server.domain.MindMap;
import dev.bytelore.server.repository.LessonRepository;
import dev.bytelore.server.repository.MindMapRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the stored package for every content row that has none, once, at startup.
 *
 * <p><strong>The condition is exactly "no stored package"</strong> -- {@code sha256 IS NULL}, which
 * by the {@code ck_*_package_complete} constraints means the bytes and the length are absent too.
 * Rows that already carry a package are not touched, not re-serialized, and not compared against
 * anything.
 *
 * <p>That last point is the design decision, not an omission. The obvious-looking alternative --
 * repackage every row and compare the result against the stored digest -- would put a second
 * serialization of the same content on the read side of the system, which is precisely the shape
 * the protocol stores bytes to rule out: two runs against different database states, one of them
 * silently becoming the answer. A row either has the bytes the write path produced or it has none.
 *
 * <p>Two situations produce rows with no package, and both are ordinary rather than exceptional.
 * Content seeded by a migration is inserted without one, because a digest written by hand in SQL
 * would be a second, unverifiable implementation of the packaging rules. And a change to the
 * package format invalidates every stored digest at once, so the format change ships as a migration
 * that clears the columns and lets this runner refill them -- a real operational event that will
 * happen again, which is why the machinery is permanent rather than a one-off script.
 *
 * <p>{@code content_version} is never incremented here. A repackaging changes how content is
 * serialized, not what it says; bumping the counter would tell every client the lesson had been
 * edited and cost them a download of text they already hold.
 */
@Component
public class ContentRepackagingRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ContentRepackagingRunner.class);

  private final LessonRepository lessons;
  private final MindMapRepository mindMaps;
  private final ContentVersionService versions;

  public ContentRepackagingRunner(
      LessonRepository lessons, MindMapRepository mindMaps, ContentVersionService versions) {
    this.lessons = lessons;
    this.mindMaps = mindMaps;
    this.versions = versions;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    // Soft-deleted lessons are packaged too. They are invisible to every read endpoint and to every
    // manifest, so the work is not needed today -- but an operator restoring one would otherwise
    // restore a row that no client can download, and the failure would surface far from its cause.
    List<Lesson> unpackagedLessons = lessons.findBySha256IsNull();
    for (Lesson lesson : unpackagedLessons) {
      versions.repackageLesson(lesson);
      lessons.save(lesson);
    }

    List<MindMap> unpackagedMindMaps = mindMaps.findBySha256IsNull();
    for (MindMap mindMap : unpackagedMindMaps) {
      versions.repackageMindMap(mindMap);
      mindMaps.save(mindMap);
    }

    if (!unpackagedLessons.isEmpty() || !unpackagedMindMaps.isEmpty()) {
      log.info(
          "Built stored packages for {} lesson(s) and {} mind map(s) that had none.",
          unpackagedLessons.size(),
          unpackagedMindMaps.size());
    }
  }
}
