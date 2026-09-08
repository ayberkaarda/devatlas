package dev.bytelore.server.corpus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import dev.bytelore.server.content.admin.AdminCodeExampleService;
import java.lang.reflect.Field;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The extension table, and the one property that keeps it honest. */
class CorpusLanguagesTest {

  /**
   * The loader's copy of the closed language set is the same set the administration API enforces.
   *
   * <p>The loader cannot call the service -- a Flyway migration runs before the service layer
   * exists -- so it holds its own copy, and a copy is a thing that drifts. This is the test that
   * notices. If it fails because the field it reflects over was renamed, that is the same finding
   * wearing a different hat: somebody changed the set and the loader was not told.
   */
  @Test
  void theLoadersLanguageSetIsTheOneTheAdministrationApiEnforces() {
    assertThat(CorpusLanguages.SUPPORTED_LANGUAGES)
        .as("the loader's copy of the closed language set")
        .isEqualTo(administrationApiLanguages());
  }

  /** Every extension the corpus format recognizes maps into the closed set, with no exceptions. */
  @Test
  void everyMappedLanguageIsInTheClosedSet() {
    for (String extension :
        Set.of(
            "ts", "js", "java", "kt", "py", "rs", "sh", "css", "yaml", "yml", "txt", "go", "rb",
            "php", "cs", "cpp", "sql", "html", "json", "xml")) {
      assertThat(CorpusLanguages.SUPPORTED_LANGUAGES)
          .as("language for .%s", extension)
          .contains(CorpusLanguages.languageForExtension("listing." + extension));
    }
  }

  /** Both spellings of a YAML file are one language, and a template is stored as html. */
  @Test
  void twoExtensionsMayShareALanguage() {
    assertThat(CorpusLanguages.languageForExtension("compose.yml")).isEqualTo("yaml");
    assertThat(CorpusLanguages.languageForExtension("compose.yaml")).isEqualTo("yaml");
    assertThat(CorpusLanguages.languageForExtension("card.html")).isEqualTo("html");
  }

  @Test
  void anUnmappedExtensionIsAnErrorRatherThanASkippedFile() {
    assertThatThrownBy(() -> CorpusLanguages.languageForExtension("card.vue"))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("does not map to a language");
  }

  @Test
  void aFileWithNoExtensionIsAnError() {
    assertThatThrownBy(() -> CorpusLanguages.languageForExtension("Makefile"))
        .isInstanceOf(CorpusException.class)
        .hasMessageContaining("no file extension");
  }

  @Test
  void expectedOutputIsRecognizedAsEvidenceRatherThanAsAListing() {
    assertThat(CorpusLanguages.isExpectedOutput("run-1.expected")).isTrue();
    assertThat(CorpusLanguages.isExpectedOutput("run-1.py")).isFalse();
  }

  @SuppressWarnings("unchecked")
  private static Set<String> administrationApiLanguages() {
    try {
      Field field = AdminCodeExampleService.class.getDeclaredField("SUPPORTED_LANGUAGES");
      field.setAccessible(true);
      return (Set<String>) field.get(null);
    } catch (ReflectiveOperationException e) {
      return fail(
          "The administration service no longer exposes a SUPPORTED_LANGUAGES field, so the"
              + " loader's copy of the closed language set cannot be compared against it. Point"
              + " this test at wherever the set moved to.",
          e);
    }
  }
}
