package dev.bytelore.server.content.packaging;

/**
 * One code example as it appears nested inside a {@link LessonPackage} (content sync protocol
 * §4.3). It carries no identifier of its own: a code example is not a downloadable entity, only a
 * lesson and a mind map are (§2), so nothing outside its own lesson ever needs to address one
 * individually.
 *
 * @param language highlighter language identifier
 * @param code source text, LF-normalized, not HTML-sanitized (§7.5 of the REST contract)
 * @param caption optional caption
 * @param order 1-based position among the lesson's code examples
 */
public record CodeExamplePackageItem(String language, String code, String caption, int order) {}
