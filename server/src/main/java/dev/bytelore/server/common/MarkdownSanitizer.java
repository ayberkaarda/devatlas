package dev.bytelore.server.common;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlChangeListener;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/**
 * The one gate that decides what markup authored content may carry, applied in the one place
 * content is written (§2.8 of the REST contract).
 *
 * <p>It is used two ways, and the difference matters.
 *
 * <p><strong>As a validator</strong>, for a markdown body or a mind-map label. The stored form is
 * markdown source, not HTML, and an HTML sanitizer's output is not markdown: the OWASP library
 * parses its input as HTML and re-serializes it, which unconditionally rewrites nine printable
 * ASCII characters as entities -- {@code " & ' + < = > @ `} -- and strips any tag it does not
 * recognize. Storing that output would mean an author could not write a code fence, an apostrophe,
 * an e-mail address or a blockquote without the server altering it, and the altered bytes are the
 * bytes that get hashed and shipped to clients. So the write boundary is a predicate, not a
 * rewrite: the body is examined, and either stored exactly as written or refused with {@link
 * ErrorCode#UNSAFE_HTML} naming what was wrong.
 *
 * <p>The examination has <strong>three</strong> parts, run in order, and the first two exist
 * because the last one was measured to miss something.
 *
 * <ol>
 *   <li><strong>Raw-markup rejection on the parse tree.</strong> Any {@code HtmlBlock} or {@code
 *       HtmlInline} node whose literal contains {@code <!} or {@code <?} -- an HTML comment, a
 *       CDATA section, a doctype, a processing instruction -- is refused outright. The sanitizer's
 *       comment lexer does not implement the HTML5 parser's comment-ending rules, so it reads
 *       {@code <!-->}, {@code <!--->} and {@code --!>} as opening a comment that never closes, and
 *       swallows everything after them. It then reports <em>nothing</em>, because its change
 *       listener fires only for a tag or attribute it discards, and text it consumed as a comment
 *       was never a tag. A browser -- and both markdown renderers in this system -- close the
 *       comment where HTML5 says and execute what follows. Measured: {@code
 *       <!--><script>alert(1)</script>-->} draws no callback at all and would have been stored. No
 *       allow-list can fix that, so the construct is refused before the sanitizer is consulted; a
 *       lesson or blog body has no use for a comment, a CDATA section or a doctype.
 *   <li><strong>Link and image destinations, resolved the way a browser resolves them.</strong> The
 *       destination is decoded, stripped of the characters a URL parser ignores, lowercased, and
 *       its scheme must be {@code http}, {@code https} or {@code mailto} -- or absent, meaning a
 *       relative reference. Neither the sanitizer's URL policy nor the markdown parser's unescaping
 *       is trusted for this: both resolve a numeric character reference only when it ends in a
 *       semicolon, and a browser resolves one that does not. Measured: {@code
 *       [x](&#106avascript:alert(1))} reaches the sanitizer as ordinary text, is reported clean,
 *       and renders in a browser as a live {@code javascript:} link, while the same input written
 *       {@code &#106;avascript:} is correctly refused. The two have to be refused alike.
 *   <li><strong>The element and attribute allow-list.</strong> The markdown is rendered to HTML and
 *       the OWASP policy is run over it with an {@link HtmlChangeListener}; anything it would
 *       discard is the reason for the refusal. Its <em>output</em> is thrown away -- only the
 *       verdict is used.
 * </ol>
 *
 * <p>Checking the rendered HTML rather than the source is what makes the third part accurate in
 * both directions: a fenced code block that merely mentions {@code <script>} renders as escaped
 * text and passes, while {@code [x](javascript:alert(1))} is written in pure markdown link syntax
 * yet fails, because the check sees what a renderer would emit rather than what the author typed.
 *
 * <p><strong>As a transformer</strong>, for the blog pipeline's feed excerpt. That input really is
 * HTML -- it comes out of an Atom {@code <content>} element -- so re-serializing it through the
 * sanitizer is the right operation, and it is the only caller that keeps the output. It is also the
 * one place in this system where stored markdown is not purely what a human wrote: the excerpt
 * embedded in an automatically generated draft is OWASP-serialized HTML, entity rewriting and all,
 * so an administrator opening such a draft in the editor sees {@code &#39;} where the feed had an
 * apostrophe. That is a deliberate, bounded exception -- it applies to automatically sourced drafts
 * only, such a draft is reviewed by a human before it can be published, and the alternative
 * (validating syndicated markup instead of transforming it) would refuse nearly every real release
 * note.
 *
 * <p>Nothing on the allow-list permits {@code <script>}, {@code <style>}, an event-handler
 * attribute, a {@code javascript:}/{@code data:} URL, or {@code <iframe>}/{@code <object>}/{@code
 * <embed>}; they are refused simply by never appearing on it, which is how an allow-list works --
 * there is no separate deny-list to keep in sync. The browser clients sanitize again with DOMPurify
 * when they render, and that second layer is what covers any construct where this server's
 * CommonMark implementation and the client's disagree.
 *
 * <p>Callers run {@link TextNormalizer#normalize(String)} first and validate the normalized value,
 * so that the bytes which end up stored, hashed and served are the bytes that were checked.
 */
public final class MarkdownSanitizer {

  /**
   * Elements and attributes the stock policies do not cover but real content needs.
   *
   * <ul>
   *   <li>{@code pre} -- absent from {@code Sanitizers.BLOCKS}, and every fenced code block renders
   *       as {@code <pre><code>}.
   *   <li>{@code code[class]} -- an info string on a fence renders as {@code class="language-ts"}.
   *       The pattern restricts the value to a language name so the attribute cannot be used to
   *       reach a stylesheet's selectors.
   *   <li>{@code hr} -- a thematic break, plain markdown ({@code ---}).
   *   <li>{@code br} -- a hard line break, plain markdown (two trailing spaces).
   *   <li>{@code details}/{@code summary} -- a collapsible "show the solution" block, which a
   *       lesson has no other way to express.
   * </ul>
   */
  private static final PolicyFactory EXTRA_ELEMENTS =
      new HtmlPolicyBuilder()
          .allowElements("pre", "code", "hr", "br", "details", "summary")
          .allowAttributes("class")
          .matching(Pattern.compile("^language-[A-Za-z0-9+#._-]{1,32}$"))
          .onElements("code")
          .toFactory();

  /**
   * The single allow-list: what an authored body may render to, and what a feed excerpt is cut down
   * to.
   */
  private static final PolicyFactory CONTENT_POLICY =
      Sanitizers.BLOCKS
          .and(Sanitizers.FORMATTING)
          .and(Sanitizers.LINKS)
          .and(Sanitizers.IMAGES)
          .and(Sanitizers.TABLES)
          .and(EXTRA_ELEMENTS);

  /**
   * No element is on the allow-list, so any tag at all is a discard. Used to assert that a field
   * which is rendered by interpolation -- a mind-map node label -- carries no markup to begin with.
   */
  private static final PolicyFactory PLAIN_TEXT_POLICY = new HtmlPolicyBuilder().toFactory();

  /**
   * Nesting limits, pinned here rather than left to the parser's defaults.
   *
   * <p>A body may be 200 000 characters, and a parser that recurses per nesting level turns that
   * size budget into a denial-of-service budget: measured on an earlier release of the parser,
   * twenty thousand unbalanced emphasis markers ended in a stack overflow and twenty thousand
   * repetitions of an unterminated comment opener cost more than four seconds of CPU for a single
   * request. Both are reachable by any authenticated editor. These values sit far above anything
   * real prose reaches -- a deeply nested list is a handful of levels -- and a document past them
   * is refused rather than allowed to consume the request thread.
   */
  private static final int MAX_OPEN_BLOCK_PARSERS = 60;

  private static final int MAX_INLINE_NESTING = 60;

  private static final Parser MARKDOWN_PARSER =
      Parser.builder()
          .maxOpenBlockParsers(MAX_OPEN_BLOCK_PARSERS)
          .maxInlineNesting(MAX_INLINE_NESTING)
          .build();

  private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder().build();

  /** Schemes a link or image in a content body may address. A relative reference has none. */
  private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "mailto");

  /**
   * A scheme as a URL parser recognizes one: a letter followed by letters, digits, {@code +},
   * {@code -} or {@code .}, up to the first colon. Anything that does not match is a relative
   * reference, which cannot navigate anywhere the current origin could not.
   */
  private static final Pattern SCHEME = Pattern.compile("^([a-z][a-z0-9+.\\-]*):");

  /**
   * Character references a browser resolves inside an attribute value, mapped to their
   * replacements. Only the ones that could assemble a scheme are listed; numeric forms are handled
   * directly by {@link #decodeCharacterReferences}. A trailing semicolon is optional and the name
   * is matched case-insensitively, both on purpose: this map has to be at least as permissive as a
   * browser, never less, and erring wide can only add refusals.
   */
  private static final Map<String, String> NAMED_REFERENCES =
      Map.ofEntries(
          Map.entry("amp", "&"),
          Map.entry("colon", ":"),
          Map.entry("sol", "/"),
          Map.entry("semi", ";"),
          Map.entry("num", "#"),
          Map.entry("lt", "<"),
          Map.entry("gt", ">"),
          Map.entry("quot", "\""),
          Map.entry("apos", "'"),
          Map.entry("tab", "\t"),
          Map.entry("newline", "\n"),
          Map.entry("nbsp", " "));

  private MarkdownSanitizer() {}

  /**
   * Admits a markdown body -- a lesson, a blog post, or a track/module translation body -- or
   * refuses the write.
   *
   * <p>The value is not modified. A {@code null} or blank body is not this method's business:
   * blankness is ordinary field validation and is reported as such by the caller.
   *
   * @param value the normalized markdown source
   * @param field the request field being validated, named in the refusal message so an author knows
   *     which of several bodies to fix
   * @throws ApiException {@link ErrorCode#UNSAFE_HTML} if the body would render disallowed markup
   */
  public static void validateMarkdown(String value, String field) {
    if (value == null || value.isEmpty()) {
      return;
    }
    String summary = "'%s' contains markup that is not allowed in a content body".formatted(field);
    Node document = parse(value, field);

    Findings findings = new Findings();
    document.accept(new RawMarkupScan(findings));
    findings.requireEmpty(summary);

    CONTENT_POLICY.sanitize(HTML_RENDERER.render(document), findings, null);
    findings.requireEmpty(summary);
  }

  /**
   * Admits a short plain-text field -- a mind-map node label -- or refuses the write.
   *
   * <p>A label is plain text by contract: clients render it by interpolation and never as markup,
   * so the server's job is to guarantee no markup is there rather than to quietly delete it. The
   * text is offered to the empty policy directly (it is not markdown and is never rendered as
   * markdown), so any construct an HTML parser reads as a tag is a refusal, and a {@code <!} or
   * {@code <?} is refused for the same reason a body's is. {@code a < b}, {@code 5 > 3}, {@code x =
   * y} and {@code a&b} are text to that parser and pass unchanged; {@code a <b} does not, and the
   * message names the element so the author can add the space.
   *
   * @param value the normalized label text
   * @param field the request field being validated, named in the refusal message
   * @throws ApiException {@link ErrorCode#UNSAFE_HTML} if the text contains markup
   */
  public static void validatePlainText(String value, String field) {
    if (value == null || value.isEmpty()) {
      return;
    }
    Findings findings = new Findings();
    if (containsRawMarkupDeclaration(value)) {
      findings.add("an HTML comment or declaration");
    }
    PLAIN_TEXT_POLICY.sanitize(value, findings, null);
    findings.requireEmpty("'%s' must be plain text".formatted(field));
  }

  /**
   * Cuts untrusted HTML down to the allow-list and returns the result.
   *
   * <p>The one caller whose input actually is HTML: the blog pipeline's feed excerpt. Everything
   * else validates instead, because everything else stores markdown.
   *
   * @param value untrusted HTML, or {@code null}
   * @return the sanitized HTML, or {@code null} if {@code value} was {@code null}
   */
  public static String sanitizeHtml(String value) {
    if (value == null) {
      return null;
    }
    return CONTENT_POLICY.sanitize(value);
  }

  private static Node parse(String value, String field) {
    try {
      return MARKDOWN_PARSER.parse(value);
    } catch (RuntimeException | StackOverflowError e) {
      // Pathological nesting is a fact about the submitted text, so it is answered as a refusal
      // rather than as a 500. The parser's pinned limits are what normally stops such input; this
      // catch is the backstop that keeps one abusive body from becoming a server error.
      throw new ApiException(
          ErrorCode.UNSAFE_HTML,
          "'%s' is nested too deeply to be checked; simplify the structure and try again."
              .formatted(field));
    }
  }

  /**
   * Whether a run of raw markup opens a construct whose end this system cannot agree on: a comment,
   * a CDATA section, a doctype or a processing instruction. Scanned anywhere in the literal rather
   * than only at its start, because a comment nested inside an allowed element is the same hazard.
   */
  private static boolean containsRawMarkupDeclaration(String literal) {
    for (int i = 0; i + 1 < literal.length(); i++) {
      if (literal.charAt(i) != '<') {
        continue;
      }
      char next = literal.charAt(i + 1);
      if (next == '!' || next == '?') {
        return true;
      }
    }
    return false;
  }

  /**
   * The scheme a browser would see in a link destination, or {@code null} for a relative reference.
   *
   * <p>Deliberately more eager than either the markdown parser or the sanitizer: character
   * references are resolved whether or not they end in a semicolon, and every character a URL
   * parser ignores -- C0 controls, {@code DEL}, and whitespace wherever it appears -- is removed
   * before the scheme is read. Being more eager can only produce more refusals, never fewer, which
   * is the right direction for a safety predicate.
   */
  static String schemeOf(String destination) {
    if (destination == null || destination.isEmpty()) {
      return null;
    }
    String decoded = decodeCharacterReferences(destination);
    StringBuilder stripped = new StringBuilder(decoded.length());
    for (int i = 0; i < decoded.length(); i++) {
      char c = decoded.charAt(i);
      if (c <= 0x20 || c == 0x7f) {
        continue;
      }
      stripped.append(c);
    }
    Matcher matcher = SCHEME.matcher(stripped.toString().toLowerCase(Locale.ROOT));
    return matcher.find() ? matcher.group(1) : null;
  }

  /**
   * Resolves numeric and named character references in one pass, semicolon or not, the way a
   * browser resolves them inside an attribute value.
   */
  private static String decodeCharacterReferences(String value) {
    if (value.indexOf('&') < 0) {
      return value;
    }
    StringBuilder out = new StringBuilder(value.length());
    int i = 0;
    while (i < value.length()) {
      char c = value.charAt(i);
      if (c != '&') {
        out.append(c);
        i++;
        continue;
      }
      int j = i + 1;
      if (j < value.length() && value.charAt(j) == '#') {
        j++;
        int radix = 10;
        if (j < value.length() && (value.charAt(j) == 'x' || value.charAt(j) == 'X')) {
          radix = 16;
          j++;
        }
        int digitsStart = j;
        while (j < value.length() && Character.digit(value.charAt(j), radix) >= 0) {
          j++;
        }
        if (j > digitsStart) {
          appendCodePoint(out, value.substring(digitsStart, j), radix);
          i = (j < value.length() && value.charAt(j) == ';') ? j + 1 : j;
          continue;
        }
      } else {
        int nameStart = j;
        while (j < value.length() && Character.isLetter(value.charAt(j))) {
          j++;
        }
        String replacement =
            NAMED_REFERENCES.get(value.substring(nameStart, j).toLowerCase(Locale.ROOT));
        if (replacement != null) {
          out.append(replacement);
          i = (j < value.length() && value.charAt(j) == ';') ? j + 1 : j;
          continue;
        }
      }
      out.append(c);
      i++;
    }
    return out.toString();
  }

  private static void appendCodePoint(StringBuilder out, String digits, int radix) {
    try {
      int codePoint = Integer.parseInt(digits, radix);
      if (Character.isValidCodePoint(codePoint)) {
        out.appendCodePoint(codePoint);
      }
    } catch (NumberFormatException e) {
      // A reference too large to be a code point resolves to nothing in a browser either. Dropping
      // it keeps the rest of the destination readable to the scheme check.
    }
  }

  /**
   * Walks the parse tree and records the two hazards the allow-list cannot see: raw markup
   * declarations, and link or image destinations whose scheme a content body may not address.
   */
  private static final class RawMarkupScan extends AbstractVisitor {

    private final Findings findings;

    RawMarkupScan(Findings findings) {
      this.findings = findings;
    }

    @Override
    public void visit(HtmlBlock htmlBlock) {
      checkLiteral(htmlBlock.getLiteral());
      visitChildren(htmlBlock);
    }

    @Override
    public void visit(HtmlInline htmlInline) {
      checkLiteral(htmlInline.getLiteral());
      visitChildren(htmlInline);
    }

    @Override
    public void visit(Link link) {
      checkDestination(link.getDestination(), "link");
      visitChildren(link);
    }

    @Override
    public void visit(Image image) {
      checkDestination(image.getDestination(), "image");
      visitChildren(image);
    }

    private void checkLiteral(String literal) {
      if (literal != null && containsRawMarkupDeclaration(literal)) {
        findings.add("an HTML comment or declaration");
      }
    }

    private void checkDestination(String destination, String kind) {
      String scheme = schemeOf(destination);
      if (scheme != null && !ALLOWED_SCHEMES.contains(scheme)) {
        findings.add("%s target with a '%s:' scheme".formatted(kind, scheme));
      }
    }
  }

  /**
   * Collects everything wrong with a body, from the parse tree and from the policy alike.
   *
   * <p>Ordering is a {@link TreeSet} rather than encounter order so that the same input always
   * produces the same message: an error message that shuffles between runs cannot be asserted on,
   * and a client that logs it sees noise.
   */
  private static final class Findings implements HtmlChangeListener<Void> {

    private final Set<String> problems = new TreeSet<>();

    void add(String problem) {
      problems.add(problem);
    }

    @Override
    public void discardedTag(Void context, String elementName) {
      problems.add("<" + elementName + ">");
    }

    @Override
    public void discardedAttributes(Void context, String tagName, String... attributeNames) {
      for (String attributeName : attributeNames) {
        problems.add(tagName + "[" + attributeName + "]");
      }
    }

    void requireEmpty(String summary) {
      if (problems.isEmpty()) {
        return;
      }
      throw new ApiException(
          ErrorCode.UNSAFE_HTML, "%s: %s.".formatted(summary, String.join(", ", problems)));
    }
  }
}
