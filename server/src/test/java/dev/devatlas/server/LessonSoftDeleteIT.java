package dev.devatlas.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.devatlas.server.common.UuidV7;
import dev.devatlas.server.domain.Difficulty;
import dev.devatlas.server.domain.Lesson;
import dev.devatlas.server.domain.Module;
import dev.devatlas.server.domain.Track;
import dev.devatlas.server.repository.LessonRepository;
import dev.devatlas.server.repository.ModuleRepository;
import dev.devatlas.server.repository.TrackRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * The behaviour {@code ux_lessons_slug} exists for: it is a <strong>partial</strong> unique index
 * (see {@code V2__content_model.sql}), {@code UNIQUE (slug) WHERE deleted_at IS NULL}, not a plain
 * unique index over {@code slug}.
 *
 * <p>A plain unique index would satisfy only one of the two rules below -- it would still stop two
 * live lessons from sharing a slug, but it would also let a soft-deleted lesson squat on its slug
 * forever, making the deleted row's address unrecreatable. Both assertions have to hold for the
 * partial index to be the right index; a test that checked only the second half would stay green
 * under a plain unique index.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class LessonSoftDeleteIT {

  @Autowired private TrackRepository tracks;
  @Autowired private ModuleRepository modules;
  @Autowired private LessonRepository lessons;

  @Test
  void aSoftDeletedLessonsSlugCanBeReusedByANewLesson() {
    Module module = newModule(newTrack());
    String slug = "reusable-slug-" + UUID.randomUUID().toString().substring(0, 8);

    Lesson original = lessons.saveAndFlush(newLesson(module, slug, 1));
    original.setDeletedAt(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    lessons.saveAndFlush(original);

    Lesson recreated = lessons.saveAndFlush(newLesson(module, slug, 2));

    assertThat(recreated.getId()).isNotEqualTo(original.getId());
    assertThat(recreated.getSlug()).isEqualTo(slug);
    assertThat(recreated.getDeletedAt()).isNull();
    assertThat(lessons.existsBySlugAndDeletedAtIsNull(slug)).isTrue();
    assertThat(lessons.findBySlugAndDeletedAtIsNull(slug).orElseThrow().getId())
        .isEqualTo(recreated.getId());
  }

  @Test
  void twoLiveLessonsCannotShareASlug() {
    Module module = newModule(newTrack());
    String slug = "unique-live-slug-" + UUID.randomUUID().toString().substring(0, 8);

    lessons.saveAndFlush(newLesson(module, slug, 1));
    Lesson second = newLesson(module, slug, 2);

    assertThatThrownBy(() -> lessons.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Track newTrack() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Track track = new Track();
    track.setId(UuidV7.randomUuid());
    track.setSlug("lesson-soft-delete-it-" + UUID.randomUUID().toString().substring(0, 8));
    track.setTitle("Lesson soft-delete fixture track");
    track.setDisplayOrder(1);
    track.setPublished(false);
    track.setContentVersion(1);
    track.setCreatedAt(now);
    track.setUpdatedAt(now);
    return tracks.saveAndFlush(track);
  }

  private Module newModule(Track track) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Module module = new Module();
    module.setId(UuidV7.randomUuid());
    module.setTrackId(track.getId());
    module.setTitle("Lesson soft-delete fixture module");
    module.setDisplayOrder(1);
    module.setCreatedAt(now);
    module.setUpdatedAt(now);
    return modules.saveAndFlush(module);
  }

  private Lesson newLesson(Module module, String slug, int displayOrder) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Lesson lesson = new Lesson();
    lesson.setId(UuidV7.randomUuid());
    lesson.setModuleId(module.getId());
    lesson.setSlug(slug);
    lesson.setTitle("Fixture lesson");
    lesson.setBodyMarkdown("Fixture body.");
    lesson.setDifficulty(Difficulty.BEGINNER);
    lesson.setDisplayOrder(displayOrder);
    lesson.setContentVersion(1);
    lesson.setCreatedAt(now);
    lesson.setUpdatedAt(now);
    return lesson;
  }
}
