package dev.bytelore.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.bytelore.server.auth.dto.AuthResponse;
import dev.bytelore.server.auth.dto.LoginRequest;
import dev.bytelore.server.common.UuidV7;
import dev.bytelore.server.domain.Role;
import dev.bytelore.server.domain.Theme;
import dev.bytelore.server.domain.User;
import dev.bytelore.server.domain.UserLocale;
import dev.bytelore.server.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared plumbing for the admin/public content Testcontainers suites: signing in as the seeded
 * administrator, minting throwaway {@code EDITOR}/{@code USER} accounts (registration always
 * produces {@code USER} -- §5.1.1 -- so anything higher is created directly through the repository,
 * the same way {@code LessonSoftDeleteIT} builds its fixtures), and tearing down a track's whole
 * subtree child-first, because the foreign keys between tracks, modules, lessons, code examples and
 * translations are deliberately not {@code ON DELETE CASCADE} (§2.10).
 *
 * <p>The Testcontainer is shared across test classes through Spring's context cache (see the class
 * Javadoc on {@code LessonSoftDeleteIT}), so every subclass must clean up its own fixtures in an
 * {@code @AfterEach} and must not assume it is the only content in the database.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class ContentApiTestSupport {

  protected static final String ADMIN_EMAIL = "admin@bytelore.test";
  protected static final String ADMIN_PASSWORD = "seed-admin-password-for-tests";

  /**
   * The track the sample seed migration inserts, addressed by the identifier that migration fixes
   * as a literal so that every environment names the same row.
   */
  protected static final UUID SEED_TRACK_ID =
      UUID.fromString("019205a0-1000-7000-8000-000000000001");

  @Autowired protected MockMvc mockMvc;
  @Autowired protected JsonMapper jsonMapper;
  @Autowired protected UserRepository users;
  @Autowired protected PasswordEncoder passwordEncoder;
  @Autowired protected DataSource dataSource;

  protected String adminToken() throws Exception {
    return login(ADMIN_EMAIL, ADMIN_PASSWORD);
  }

  protected String editorToken() throws Exception {
    return createUserAndLogin(Role.EDITOR);
  }

  protected String userToken() throws Exception {
    return createUserAndLogin(Role.USER);
  }

  private String createUserAndLogin(Role role) throws Exception {
    String email =
        role.name().toLowerCase(java.util.Locale.ROOT) + "-" + UUID.randomUUID() + "@example.test";
    String password = "fixture-password-2026";

    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    User user = new User();
    user.setId(UuidV7.randomUuid());
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(role);
    user.setLocale(UserLocale.EN);
    user.setTheme(Theme.SYSTEM);
    user.setEnabled(true);
    user.setCreatedAt(now);
    user.setUpdatedAt(now);
    users.save(user);

    return login(email, password);
  }

  private String login(String email, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        jsonMapper.writeValueAsString(
                            new LoginRequest(email, password, "it-device", "BODY"))))
            .andReturn();
    if (result.getResponse().getStatus() != 200) {
      throw new IllegalStateException(
          "Fixture login failed for '%s': %s"
              .formatted(email, result.getResponse().getContentAsString()));
    }
    AuthResponse session =
        jsonMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    return session.accessToken();
  }

  protected JsonNode json(MvcResult result) throws Exception {
    return jsonMapper.readTree(result.getResponse().getContentAsString());
  }

  protected String errorCode(MvcResult result) throws Exception {
    return json(result).path("code").asString();
  }

  /**
   * Sets a track's published flag directly and answers with the value it replaced.
   *
   * <p>Publication is an editorial decision about content, recorded on the row and changed by
   * migrations whenever a person decides a track is ready. A suite that reads the public endpoints
   * needs a published track to read; it has no opinion about which content an editor chose to
   * publish, and inheriting that choice is what makes an editorial decision able to turn a read
   * test red. So the arrangement is written here, explicitly, instead of being taken from whatever
   * the database happens to hold.
   *
   * <p>The flag is written through SQL rather than the administration API on purpose: the API
   * requires an editor session and would bump the track's content version, and a read test that
   * changed the version of the content it is about to read would be arranging more than it needs.
   *
   * <p>The previous value is returned so that the caller can put it back. The Testcontainer is
   * shared across test classes through Spring's context cache, so a flag left flipped is a flag the
   * next class inherits.
   *
   * @return the value the column held before this call
   */
  protected boolean setTrackPublished(UUID trackId, boolean published) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Boolean previous =
        jdbc.queryForObject("SELECT published FROM tracks WHERE id = ?", Boolean.class, trackId);
    if (previous == null) {
      throw new IllegalStateException("No track exists with id " + trackId);
    }
    jdbc.update("UPDATE tracks SET published = ? WHERE id = ?", published, trackId);
    return previous;
  }

  /** Deletes a track and everything beneath it, child-first, bypassing every business rule. */
  protected void deleteTrackTree(UUID trackId) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.update(
        "DELETE FROM content_translations WHERE entity_type = 'LESSON' AND entity_id IN "
            + "(SELECT id FROM lessons WHERE module_id IN (SELECT id FROM modules WHERE track_id = ?))",
        trackId);
    jdbc.update(
        "DELETE FROM content_translations WHERE entity_type = 'MODULE' AND entity_id IN "
            + "(SELECT id FROM modules WHERE track_id = ?)",
        trackId);
    jdbc.update(
        "DELETE FROM content_translations WHERE entity_type = 'TRACK' AND entity_id = ?", trackId);
    jdbc.update(
        "DELETE FROM code_examples WHERE lesson_id IN "
            + "(SELECT id FROM lessons WHERE module_id IN (SELECT id FROM modules WHERE track_id = ?))",
        trackId);
    jdbc.update(
        "DELETE FROM lessons WHERE module_id IN (SELECT id FROM modules WHERE track_id = ?)",
        trackId);
    jdbc.update("DELETE FROM mind_maps WHERE track_id = ?", trackId);
    jdbc.update("DELETE FROM modules WHERE track_id = ?", trackId);
    jdbc.update("DELETE FROM tracks WHERE id = ?", trackId);
  }

  protected static String uniqueSlug(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }
}
