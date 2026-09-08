package dev.bytelore.server.corpus;

import dev.bytelore.server.corpus.ParsedCorpus.ParsedCodeExample;
import dev.bytelore.server.corpus.ParsedCorpus.ParsedLesson;
import dev.bytelore.server.corpus.ParsedCorpus.ParsedModule;
import dev.bytelore.server.corpus.ParsedCorpus.ParsedTrack;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Writes a parsed corpus into the content tables, by identifier, inserting what is not there and
 * updating what is.
 *
 * <p>Plain JDBC on the migration's own connection rather than the repositories: a Flyway migration
 * runs before the entity manager exists, and a loader that needed the service layer could not run
 * where it has to run. Everything it writes has already been normalized and validated by {@link
 * CorpusParser}; there is no check and no transformation here, which is what keeps the boundary in
 * one place.
 *
 * <p>Three rules govern this class, and each of them is a rule about what it must <em>not</em> do.
 *
 * <p><strong>It never computes a digest.</strong> A row it inserts gets null in all three package
 * columns, and a row whose content it changes gets them set back to null. The bytes and the digest
 * over them are produced by the packaging step that runs at startup, in the one place that owns the
 * canonical serialization; a second implementation living in a migration would be unreachable from
 * the determinism tests and would drift from the first the moment the package format changed.
 *
 * <p><strong>It never publishes.</strong> A track is inserted unpublished, and on any later run the
 * {@code published} column is not in the update statement at all. The public read paths serve only
 * published tracks, so a freshly loaded corpus is invisible in the product until a person commits a
 * one-line migration that flips the flag for one track. That commit is the approval, and version
 * control attributes it; nothing automatically produced is published without a human saying so.
 *
 * <p><strong>It never deletes a lesson or a module.</strong> A later corpus version holds only the
 * files that changed, so a lesson's absence from a corpus means "unchanged", not "withdrawn".
 * Listings are the exception: a lesson that appears in a corpus brings its complete set of them, so
 * one that is no longer listed is genuinely gone.
 */
public final class CorpusWriter {

  private final Connection connection;
  private final OffsetDateTime timestamp;

  public CorpusWriter(Connection connection, Instant now) {
    this.connection = connection;
    this.timestamp = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
  }

  /**
   * Writes every track of the corpus.
   *
   * @return how many tracks were written
   */
  public int write(ParsedCorpus corpus) throws SQLException {
    for (ParsedTrack track : corpus.tracks()) {
      writeTrack(track);
    }
    return corpus.tracks().size();
  }

  private void writeTrack(ParsedTrack track) throws SQLException {
    boolean changed = upsertTrack(track);

    for (ParsedModule module : track.modules()) {
      changed |= upsertModule(track.id(), module);
      for (ParsedLesson lesson : module.lessons()) {
        changed |= upsertLesson(module.id(), lesson);
      }
    }
    changed |= upsertMindMap(track);

    if (changed) {
      bumpTrackContentVersion(track.id());
    }
  }

  private boolean upsertTrack(ParsedTrack track) throws SQLException {
    Map<String, Object> existing =
        selectRow(
            "SELECT slug, title, description, icon, display_order FROM tracks WHERE id = ?",
            track.id());

    if (existing == null) {
      String sql =
          """
          INSERT INTO tracks (id, slug, title, description, icon, display_order, published,
                              content_version, created_at, updated_at, version)
          VALUES (?, ?, ?, ?, ?, ?, false, 1, ?, ?, 0)
          """;
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setObject(1, track.id());
        statement.setString(2, track.slug());
        statement.setString(3, track.title());
        statement.setString(4, track.description());
        setNullableString(statement, 5, track.icon());
        statement.setInt(6, track.displayOrder());
        statement.setObject(7, timestamp);
        statement.setObject(8, timestamp);
        statement.executeUpdate();
      }
      return true;
    }

    boolean changed =
        !Objects.equals(existing.get("slug"), track.slug())
            || !Objects.equals(existing.get("title"), track.title())
            || !Objects.equals(existing.get("description"), track.description())
            || !Objects.equals(existing.get("icon"), track.icon())
            || !Objects.equals(existing.get("display_order"), track.displayOrder());
    if (!changed) {
      return false;
    }

    // `published` is deliberately absent from this statement. Publication is a separate migration
    // written by a person, and a loader that touched the column could un-publish a reviewed track
    // by re-running.
    String sql =
        """
        UPDATE tracks
           SET slug = ?, title = ?, description = ?, icon = ?, display_order = ?,
               updated_at = ?, version = version + 1
         WHERE id = ?
        """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, track.slug());
      statement.setString(2, track.title());
      statement.setString(3, track.description());
      setNullableString(statement, 4, track.icon());
      statement.setInt(5, track.displayOrder());
      statement.setObject(6, timestamp);
      statement.setObject(7, track.id());
      statement.executeUpdate();
    }
    return true;
  }

  private boolean upsertModule(UUID trackId, ParsedModule module) throws SQLException {
    Map<String, Object> existing =
        selectRow(
            "SELECT track_id, title, display_order, estimated_minutes FROM modules WHERE id = ?",
            module.id());

    if (existing == null) {
      String sql =
          """
          INSERT INTO modules (id, track_id, title, display_order, estimated_minutes,
                               created_at, updated_at, version)
          VALUES (?, ?, ?, ?, ?, ?, ?, 0)
          """;
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setObject(1, module.id());
        statement.setObject(2, trackId);
        statement.setString(3, module.title());
        statement.setInt(4, module.displayOrder());
        setNullableInt(statement, 5, module.estimatedMinutes());
        statement.setObject(6, timestamp);
        statement.setObject(7, timestamp);
        statement.executeUpdate();
      }
      return true;
    }

    boolean changed =
        !Objects.equals(existing.get("track_id"), trackId)
            || !Objects.equals(existing.get("title"), module.title())
            || !Objects.equals(existing.get("display_order"), module.displayOrder())
            || !Objects.equals(existing.get("estimated_minutes"), module.estimatedMinutes());
    if (!changed) {
      return false;
    }

    String sql =
        """
        UPDATE modules
           SET track_id = ?, title = ?, display_order = ?, estimated_minutes = ?,
               updated_at = ?, version = version + 1
         WHERE id = ?
        """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, trackId);
      statement.setString(2, module.title());
      statement.setInt(3, module.displayOrder());
      setNullableInt(statement, 4, module.estimatedMinutes());
      statement.setObject(5, timestamp);
      statement.setObject(6, module.id());
      statement.executeUpdate();
    }
    return true;
  }

  private boolean upsertLesson(UUID moduleId, ParsedLesson lesson) throws SQLException {
    Map<String, Object> existing =
        selectRow(
            """
            SELECT module_id, slug, title, body_markdown, difficulty, estimated_minutes,
                   display_order
              FROM lessons WHERE id = ?
            """,
            lesson.id());

    boolean inserted = existing == null;
    if (inserted) {
      String sql =
          """
          INSERT INTO lessons (id, module_id, slug, title, body_markdown, difficulty,
                               estimated_minutes, display_order, content_version,
                               sha256, package_bytes, package_size_bytes,
                               created_at, updated_at, version)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, NULL, NULL, NULL, ?, ?, 0)
          """;
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setObject(1, lesson.id());
        statement.setObject(2, moduleId);
        statement.setString(3, lesson.slug());
        statement.setString(4, lesson.title());
        statement.setString(5, lesson.bodyMarkdown());
        statement.setString(6, lesson.difficulty());
        setNullableInt(statement, 7, lesson.estimatedMinutes());
        statement.setInt(8, lesson.displayOrder());
        statement.setObject(9, timestamp);
        statement.setObject(10, timestamp);
        statement.executeUpdate();
      }
    }

    // Always reconciled, insert or update alike -- a freshly inserted lesson has no listings yet
    // and this is what writes them. Its answer only matters on the update path, where it is one of
    // the two things that make a lesson's content different from what was stored.
    boolean listingsChanged = writeCodeExamples(lesson);

    if (inserted) {
      return true;
    }

    boolean lessonChanged =
        !Objects.equals(existing.get("module_id"), moduleId)
            || !Objects.equals(existing.get("slug"), lesson.slug())
            || !Objects.equals(existing.get("title"), lesson.title())
            || !Objects.equals(existing.get("body_markdown"), lesson.bodyMarkdown())
            || !Objects.equals(existing.get("difficulty"), lesson.difficulty())
            || !Objects.equals(existing.get("estimated_minutes"), lesson.estimatedMinutes())
            || !Objects.equals(existing.get("display_order"), lesson.displayOrder());

    if (!lessonChanged && !listingsChanged) {
      return false;
    }

    // The content changed, so the stored package describes something that no longer exists. The
    // three columns go back to null together and the packaging step at startup rebuilds them.
    String sql =
        """
        UPDATE lessons
           SET module_id = ?, slug = ?, title = ?, body_markdown = ?, difficulty = ?,
               estimated_minutes = ?, display_order = ?,
               content_version = content_version + 1,
               sha256 = NULL, package_bytes = NULL, package_size_bytes = NULL,
               updated_at = ?, version = version + 1
         WHERE id = ?
        """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, moduleId);
      statement.setString(2, lesson.slug());
      statement.setString(3, lesson.title());
      statement.setString(4, lesson.bodyMarkdown());
      statement.setString(5, lesson.difficulty());
      setNullableInt(statement, 6, lesson.estimatedMinutes());
      statement.setInt(7, lesson.displayOrder());
      statement.setObject(8, timestamp);
      statement.setObject(9, lesson.id());
      statement.executeUpdate();
    }
    return true;
  }

  /**
   * Reconciles a lesson's listings with the ones the corpus declares.
   *
   * @return whether anything about them changed, which makes it a change to the lesson's content
   */
  private boolean writeCodeExamples(ParsedLesson lesson) throws SQLException {
    Map<UUID, Map<String, Object>> existing = new LinkedHashMap<>();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "SELECT id, language, code, caption, display_order FROM code_examples"
                + " WHERE lesson_id = ?")) {
      statement.setObject(1, lesson.id());
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          Map<String, Object> row = new HashMap<>();
          row.put("language", rows.getString("language"));
          row.put("code", rows.getString("code"));
          row.put("caption", rows.getString("caption"));
          row.put("display_order", rows.getInt("display_order"));
          existing.put(rows.getObject("id", UUID.class), row);
        }
      }
    }

    boolean changed = false;
    List<UUID> declared = new ArrayList<>();
    for (ParsedCodeExample example : lesson.codeExamples()) {
      declared.add(example.id());
      Map<String, Object> row = existing.get(example.id());
      if (row == null) {
        try (PreparedStatement statement =
            connection.prepareStatement(
                """
                INSERT INTO code_examples (id, lesson_id, language, code, caption, display_order,
                                           created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """)) {
          statement.setObject(1, example.id());
          statement.setObject(2, lesson.id());
          statement.setString(3, example.language());
          statement.setString(4, example.code());
          setNullableString(statement, 5, example.caption());
          statement.setInt(6, example.displayOrder());
          statement.setObject(7, timestamp);
          statement.setObject(8, timestamp);
          statement.executeUpdate();
        }
        changed = true;
        continue;
      }
      boolean same =
          Objects.equals(row.get("language"), example.language())
              && Objects.equals(row.get("code"), example.code())
              && Objects.equals(row.get("caption"), example.caption())
              && Objects.equals(row.get("display_order"), example.displayOrder());
      if (same) {
        continue;
      }
      try (PreparedStatement statement =
          connection.prepareStatement(
              """
              UPDATE code_examples
                 SET language = ?, code = ?, caption = ?, display_order = ?,
                     updated_at = ?, version = version + 1
               WHERE id = ?
              """)) {
        statement.setString(1, example.language());
        statement.setString(2, example.code());
        setNullableString(statement, 3, example.caption());
        statement.setInt(4, example.displayOrder());
        statement.setObject(5, timestamp);
        statement.setObject(6, example.id());
        statement.executeUpdate();
      }
      changed = true;
    }

    for (UUID id : existing.keySet()) {
      if (declared.contains(id)) {
        continue;
      }
      try (PreparedStatement statement =
          connection.prepareStatement("DELETE FROM code_examples WHERE id = ?")) {
        statement.setObject(1, id);
        statement.executeUpdate();
      }
      changed = true;
    }
    return changed;
  }

  private boolean upsertMindMap(ParsedTrack track) throws SQLException {
    Map<String, Object> existing =
        selectRow("SELECT root::text AS root FROM mind_maps WHERE id = ?", track.mindMapId());

    if (existing == null) {
      String sql =
          """
          INSERT INTO mind_maps (id, track_id, root, content_version,
                                 sha256, package_bytes, package_size_bytes,
                                 created_at, updated_at, version)
          VALUES (?, ?, ?::jsonb, 1, NULL, NULL, NULL, ?, ?, 0)
          """;
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setObject(1, track.mindMapId());
        statement.setObject(2, track.id());
        statement.setString(3, track.mindMapRoot());
        statement.setObject(4, timestamp);
        statement.setObject(5, timestamp);
        statement.executeUpdate();
      }
      return true;
    }

    // Compared as jsonb rather than as text: Postgres normalizes key order and whitespace when it
    // stores a jsonb value, so the text that comes back is not the text that went in and a string
    // comparison would report a change on every run.
    boolean same;
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT root = ?::jsonb FROM mind_maps WHERE id = ?")) {
      statement.setString(1, track.mindMapRoot());
      statement.setObject(2, track.mindMapId());
      try (ResultSet rows = statement.executeQuery()) {
        same = rows.next() && rows.getBoolean(1);
      }
    }
    if (same) {
      return false;
    }

    String sql =
        """
        UPDATE mind_maps
           SET root = ?::jsonb, content_version = content_version + 1,
               sha256 = NULL, package_bytes = NULL, package_size_bytes = NULL,
               updated_at = ?, version = version + 1
         WHERE id = ?
        """;
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, track.mindMapRoot());
      statement.setObject(2, timestamp);
      statement.setObject(3, track.mindMapId());
      statement.executeUpdate();
    }
    return true;
  }

  private void bumpTrackContentVersion(UUID trackId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "UPDATE tracks SET content_version = content_version + 1, updated_at = ? WHERE id = ?")) {
      statement.setObject(1, timestamp);
      statement.setObject(2, trackId);
      statement.executeUpdate();
    }
  }

  private Map<String, Object> selectRow(String sql, UUID id) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, id);
      try (ResultSet rows = statement.executeQuery()) {
        if (!rows.next()) {
          return null;
        }
        Map<String, Object> row = new HashMap<>();
        var metadata = rows.getMetaData();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
          String label = metadata.getColumnLabel(i);
          Object value =
              switch (metadata.getColumnType(i)) {
                case Types.INTEGER -> {
                  int number = rows.getInt(i);
                  yield rows.wasNull() ? null : number;
                }
                case Types.OTHER -> rows.getObject(i, UUID.class);
                default -> rows.getObject(i);
              };
          row.put(label, value);
        }
        return row;
      }
    }
  }

  private static void setNullableString(PreparedStatement statement, int index, String value)
      throws SQLException {
    if (value == null) {
      statement.setNull(index, Types.VARCHAR);
    } else {
      statement.setString(index, value);
    }
  }

  private static void setNullableInt(PreparedStatement statement, int index, Integer value)
      throws SQLException {
    if (value == null) {
      statement.setNull(index, Types.INTEGER);
    } else {
      statement.setInt(index, value);
    }
  }
}
