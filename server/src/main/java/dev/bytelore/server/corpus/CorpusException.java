package dev.bytelore.server.corpus;

/**
 * A refusal by the corpus loader.
 *
 * <p>Thrown out of the Flyway migration that loads the corpus, so a refusal rolls the migration
 * back, fails startup and fails the build. There is deliberately no "log it and skip" path: content
 * that would be refused over HTTP has to be refused here too, and a refusal that only produced a
 * warning would let exactly the content this loader exists to stop reach a database.
 *
 * <p>The message always names the file it came from, because the reader of this exception is an
 * author looking for one broken file among several hundred.
 */
public class CorpusException extends RuntimeException {

  public CorpusException(String message) {
    super(message);
  }

  public CorpusException(String message, Throwable cause) {
    super(message, cause);
  }
}
