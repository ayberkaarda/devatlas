package dev.bytelore.server.corpus;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The two closed sets a code listing has to satisfy: the extensions the corpus format recognizes,
 * and the language identifiers the highlighter supports.
 *
 * <p>They are separate on purpose. The extension table is a corpus-format concern -- it decides
 * which files in an {@code examples/} directory are listings at all -- while the language set is
 * the API's, defined by §5.4.5 of the REST contract and enforced on every code example written
 * through the administration routes. The table maps <em>into</em> the set and may never introduce a
 * value the set does not hold; {@link #languageForExtension} checks that on every call rather than
 * trusting the table to have stayed in step, and a test asserts the same property against the
 * administration service's own copy so the two cannot drift apart silently.
 *
 * <p>An extension that is not in the table is an error, never a skipped file. Silently ignoring an
 * unrecognized file in an {@code examples/} directory is how a listing goes missing from a lesson
 * without anybody noticing -- the file is there, the lesson renders, and only the reader discovers
 * that the code being discussed is absent.
 */
public final class CorpusLanguages {

  /**
   * The highlighter-supported language identifiers. A duplicate of the administration service's
   * closed set, held here because a Flyway migration runs before the application's service layer
   * exists and cannot call into it. {@code CorpusLanguagesTest} compares the two and fails if they
   * disagree.
   */
  public static final Set<String> SUPPORTED_LANGUAGES =
      Set.of(
          "typescript",
          "javascript",
          "java",
          "rust",
          "sql",
          "bash",
          "json",
          "yaml",
          "html",
          "css",
          "xml",
          "kotlin",
          "python",
          "csharp",
          "cpp",
          "go",
          "php",
          "ruby",
          "text");

  /**
   * Extension (without the dot, lower case) to language identifier.
   *
   * <p>A component template -- Angular, Vue, Django -- is stored as {@code html}. There is no
   * {@code vue} or {@code jinja} identifier in the closed set, and adding one here rather than
   * changing the set deliberately would smuggle a value past the boundary the set exists to be.
   */
  private static final Map<String, String> EXTENSION_LANGUAGES =
      Map.ofEntries(
          Map.entry("ts", "typescript"),
          Map.entry("js", "javascript"),
          Map.entry("java", "java"),
          Map.entry("kt", "kotlin"),
          Map.entry("py", "python"),
          Map.entry("rs", "rust"),
          Map.entry("sh", "bash"),
          Map.entry("css", "css"),
          Map.entry("yaml", "yaml"),
          Map.entry("yml", "yaml"),
          Map.entry("txt", "text"),
          Map.entry("go", "go"),
          Map.entry("rb", "ruby"),
          Map.entry("php", "php"),
          Map.entry("cs", "csharp"),
          Map.entry("cpp", "cpp"),
          Map.entry("sql", "sql"),
          Map.entry("html", "html"),
          Map.entry("json", "json"),
          Map.entry("xml", "xml"));

  /**
   * The extension carried by a file that lives beside the listings as evidence rather than as
   * product: the output a listing is expected to produce, compared against a real run by the
   * verification runner. It is in the table so that the loader can tell it apart from a listing it
   * failed to recognize, and it is never stored.
   */
  public static final String EXPECTED_OUTPUT_EXTENSION = "expected";

  private CorpusLanguages() {}

  /**
   * The language identifier a listing with this file name is stored under.
   *
   * @param fileName the listing's file name, extension included
   * @return the language identifier, always a member of {@link #SUPPORTED_LANGUAGES}
   * @throws CorpusException if the file has no extension, if the extension is not in the table, or
   *     if the table's answer is not one the API would accept
   */
  public static String languageForExtension(String fileName) {
    String extension = extensionOf(fileName);
    if (extension == null) {
      throw new CorpusException(
          "Listing '%s' has no file extension, so its language cannot be determined."
              .formatted(fileName));
    }
    String language = EXTENSION_LANGUAGES.get(extension);
    if (language == null) {
      throw new CorpusException(
          "Listing '%s' has extension '.%s', which the corpus format does not map to a language."
              .formatted(fileName, extension));
    }
    if (!SUPPORTED_LANGUAGES.contains(language)) {
      throw new CorpusException(
          "Listing '%s' maps to language '%s', which is not one of the supported code example"
                  .formatted(fileName, language)
              + " languages.");
    }
    return language;
  }

  /** Whether this file is expected output -- evidence for the verification runner, never stored. */
  public static boolean isExpectedOutput(String fileName) {
    return EXPECTED_OUTPUT_EXTENSION.equals(extensionOf(fileName));
  }

  private static String extensionOf(String fileName) {
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
      return null;
    }
    return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
  }
}
