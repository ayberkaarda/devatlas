# 0002. Markdown safety at the write boundary

## Status

Accepted, and amended before implementation by the measurements in
"Revision: the verdict alone is not a gate" below.

## Context

Every markdown body this server stores — a lesson, a blog post, a track or
module translation — passes through `MarkdownSanitizer.sanitizeMarkdown` on
write, and mind-map labels pass through `sanitizePlainText`. Both run the OWASP
Java HTML Sanitizer (`owasp-java-html-sanitizer` 20260313.1) over the text and
store what it returns. The REST contract (`docs/protocol/rest-api.md`, §2.8)
describes this as "sanitized on write, before persistence", and the clients
render the stored markdown with `marked` and DOMPurify
(`frontend/src/app/core/markdown/markdown.service.ts`) as a second layer.

The first layer is applied to the wrong thing. An HTML sanitizer parses its
input as HTML and re-serializes it; markdown source is not HTML, and the
re-serialization rewrites it. Measured on this machine against the exact
library version the server uses, a markdown body containing a fenced code
block comes back as:

```
sent    : ```ts\nconst answer: number = 42;\n```
stored  : &#96;&#96;&#96;ts\nconst answer: number &#61; 42;\n&#96;&#96;&#96;
```

The encoding is not limited to backticks and equals signs. Passing every
printable ASCII character through the sanitizer's text path shows nine of
them are rewritten as entities, unconditionally:

```
"  →  &#34;      &  →  &amp;      '  →  &#39;      +  →  &#43;
<  →  &lt;       =  →  &#61;      >  →  &gt;       @  →  &#64;
`  →  &#96;
```

So the damage reaches well beyond code: every apostrophe in prose, every
e-mail address, every `>` that opens a blockquote, and every `<` in a sentence
like "a < b" is rewritten in the stored bytes. Mind-map labels suffer the same
way (`x = y` is stored as `x &#61; y`). Raw HTML that markdown allows inline is
mangled too — `<details><summary>More</summary>hidden</details>` is stored as
`Morehidden`, because those elements are not on the policy's allow-list and
the sanitizer strips tags it does not know rather than refusing them.

The library offers no switch for this. Its public API
(`HtmlPolicyBuilder`, `PolicyFactory`, `HtmlStreamRenderer`) has no option
that affects text encoding; the encoder (`Encoding.encodePcdataOnto`) is
package-private and unconditional. It is a deliberate defence of the library
against browser attribute-parsing quirks, and it is correct for HTML output.
It cannot be configured away because it was never meant to be.

The bug has been invisible until now for one reason: every lesson body in the
database came from the Flyway seed (`V5__seed_content.sql`), which inserts
text directly and never crosses the write boundary. The seed happens to
contain no backtick at all, and its apostrophes go in untouched. Anything
written through the admin API, the translation API or the blog pipeline since
the sanitizer was introduced is affected — and lesson bodies are then
packaged, hashed and served to the desktop client through the manifest
protocol (`docs/protocol/content-sync.md`), so the corruption is in the bytes
the digest describes, not only on screen.

Three things are fixed and not up for negotiation here:

1. **Two layers stay.** The product requirement is server-side sanitization
   with the OWASP library and client-side sanitization with DOMPurify. This
   decision may change what the server layer *does*; it may not remove it.
2. **The stored format is markdown source**, and it is served as such. The
   read API and both clients consume markdown; a server that stored rendered
   HTML would change the contract of every content endpoint and the package
   format the digests are computed over.
3. **The stored bytes are the hashed bytes are the served bytes.** Text
   normalization happens once, on write (`TextNormalizer`), and the content
   digest is a function of the stored text. Whatever the write boundary does
   must be deterministic and must happen before storage, never at read or
   hash time.

## Options considered

### A. Keep sanitizing the markdown source (status quo)

- Plus: nothing to change.
- Minus: measured above. Nine printable characters rewritten in every body,
  blockquotes destroyed, inline HTML stripped silently. A developer-education
  product whose lesson bodies cannot contain a backtick or an apostrophe is
  not a product. Rejected outright; listed only so the measurement is on
  record.

### B. Render markdown to HTML on the server, sanitize the HTML, store the HTML

Applying an HTML sanitizer to HTML is what the library is for, and the
encoding it performs is harmless in HTML output.

- Plus: the sanitizer is used as designed; its output is safe by
  construction.
- Minus: violates fixed point 2. The content endpoints, the lesson package
  shape, the translation body, the admin editor (which round-trips the body
  for editing) and both clients' renderers all consume markdown. Storing HTML
  means either changing all of them or storing both forms — and a stored HTML
  copy that is derived from the markdown is exactly the "two implementations
  of one rule" a single stored form exists to avoid.
- Minus: the encoded output is still what the editor would load back into a
  textarea, so an author would see entities in their own text.

### C. Markdown-aware surgery on the source

Parse the markdown, locate the raw-HTML nodes (`HtmlBlock`, `HtmlInline`)
by source position, run only those spans through the sanitizer, and splice
the results back into the otherwise untouched source.

- Plus: preserves every byte of the non-HTML markdown, including code fences,
  which the parser classifies as code rather than HTML, so a lesson teaching
  `<script>` inside a fence keeps it.
- Minus: the spliced spans still come out of the sanitizer's serializer, so
  a `=` or `'` *inside* an allowed inline element is still rewritten. The
  corruption shrinks; it does not go away.
- Minus: correctness depends on the parser's source-span reporting matching
  its own block/inline boundaries exactly, across nested constructs (a raw
  tag inside a list item inside a blockquote). That is a bespoke piece of
  text surgery to own and test, for a result that is still not
  byte-preserving.

### D. Drop the server layer; rely on DOMPurify at render time

- Plus: simplest possible change; the client layer already exists, is
  exercised by tests, and is the one that sees the HTML a renderer actually
  produced.
- Minus: violates fixed point 1. It also leaves the API serving whatever was
  posted, so any future consumer that renders content without DOMPurify — a
  server-rendered page for search engines, a feed, an e-mail digest — starts
  from unsanitized text. Two layers are cheap; one layer with a plan to be
  careful is how stored XSS gets shipped.

### E. Validate the rendered HTML, store the source

Render the markdown to HTML on the server with a markdown parser, run that
HTML through the OWASP sanitizer with an `HtmlChangeListener`, and treat any
callback — a discarded tag, a discarded attribute — as a rejection of the
write. The sanitizer's *output* is thrown away; only its *verdict* is used.
What is stored is the normalized markdown source, byte for byte.

The listener API exists precisely for this: `PolicyFactory.sanitize(String,
HtmlChangeListener<CTX>, CTX)` reports `discardedTag(ctx, name)` and
`discardedAttributes(ctx, tag, names...)`. Measured against the library and
`commonmark-java` 0.28.0:

| Markdown input | Listener reports |
|---|---|
| ```` ```html\n<script>alert(1)</script>\n``` ```` | nothing for the script — the fence renders as escaped text (`pre` is flagged only because the stock `BLOCKS` policy lacks it; see below) |
| `a = b and `x = 1` and a < b` | nothing |
| `[x](javascript:alert(1))` | `tag:a` |
| `<img src=x onerror="alert(2)">` | `attrs:img[onerror]` |
| `<script>alert(1)</script>` | `tag:script` |
| `<details><summary>More</summary>hidden</details>` | `tag:details`, `tag:summary` |

So the check distinguishes exactly what matters: code that *mentions* a
script tag passes, a script tag passes through as markup fails, and a
`javascript:` link fails even though it is written in markdown link syntax,
because the check sees what the renderer would emit, not what the author
typed.

- Plus: byte-preserving. The write boundary becomes a pure predicate over the
  text; storage, digest and serving see the author's normalized source.
- Plus: the server layer keeps its teeth. Nothing outside the allow-list is
  ever stored; it is refused with a `422` naming the offending elements,
  instead of being silently rewritten into something the author did not
  write.
- Plus: the same policy object serves the one place where transformation
  *is* correct — the blog pipeline's feed excerpt, which is HTML from an Atom
  `<content>` element, not markdown. Sanitizing that HTML before it is
  templated into a draft keeps the draft's embedded HTML inside the same
  allow-list the validator enforces, so a pipeline draft always passes the
  validator it is then subjected to.
- Minus: a new dependency, `org.commonmark:commonmark` 0.28.0 — 218 KB, no
  runtime dependencies of its own, BSD-2-Clause. It is a CommonMark reference
  implementation, not a rendering framework; the server uses it only to
  produce the HTML the sanitizer inspects.
- Minus: authors lose lenience. Pasting content with a `<font>` or
  `<center>` tag is now a refusal with a reason rather than a silent strip.
  For an editor with a preview pane that is the better behaviour; it is still
  a behaviour change the contract must state.
- Minus: the server's renderer and the client's renderer (`marked`) are two
  implementations of CommonMark. They agree on the specification's core, but
  a construct one treats as raw HTML and the other as text would be checked
  on one side and rendered on the other. The client's DOMPurify layer is what
  covers that gap — which is the point of having two layers.

The stock policy needs extending for this to work on real content: measured,
`Sanitizers.BLOCKS` does not include `pre`, and the renderer emits
`<code class="language-ts">` for a fenced block, so `pre`, `code[class]`
(pattern `language-[A-Za-z0-9+#.-]+`), `hr` and `br` must be allowed, and
`details`/`summary` are a product choice (a lesson's "show solution" block)
that this decision allows. The final allow-list is measured against the seed
content and a sample of GitHub release-notes HTML before it is frozen.

Mind-map labels follow the same principle with the empty policy: a label is
plain text by contract (it is rendered by interpolation, never as markup —
see ADR-0001), so the server rejects a label in which the sanitizer discards
a tag and stores the text untouched otherwise. Measured: `a < b`, `5 > 3`,
`x = y` and `a&b` produce no callback; `a <b` and `<b>x</b>` do. The first of
those is a legitimate label that would now be refused; the message names the
element and the author adds a space. That is accepted.

## Decision

**Option E.** The server renders authored markdown to HTML, asks the OWASP
sanitizer whether it would discard anything, refuses the write if so
(`422 UNSAFE_HTML`, listing the discarded elements and attributes), and stores
the normalized source unchanged. One allow-list policy is shared by the
validator (authored markdown, mind-map labels with the empty policy) and by
the transformer (feed HTML in the blog pipeline, the only input that actually
is HTML).

The sanitizer's verdict turned out to be a necessary part of that gate but
not a sufficient one; the revision below says what else the gate runs, and
why the two additions do not depend on any library's lexer.

This keeps both layers, keeps markdown as the stored and served form, and
keeps the write boundary a deterministic function of the input — the three
fixed points — while removing the one thing that was wrong: a sanitizer's
serializer standing between an author and their own bytes.

The server layer is reinterpreted, not weakened: "sanitize on write" now
means "nothing outside the allow-list is ever stored" rather than "the
stored text is the sanitizer's output". The contract's §2.8 sentence "what
you `POST` is not necessarily what you `GET` back" is retracted; what you
`POST`, normalized, is exactly what you `GET` back, or the write is refused.

### Revision: the verdict alone is not a gate

An adversarial review measured the mechanism above against a browser rather
than against the sanitizer's own output, and found two ways through it. Both
are recorded here because they are the reason the gate has three parts
instead of one.

**A verdict reports discards, not swallows.** `HtmlChangeListener` fires when
the policy *drops* an element or attribute. It says nothing about input the
policy consumed as a comment — and the sanitizer's comment lexer does not
agree with HTML5 about where a comment ends: it does not recognise `<!-->`,
`<!--->`, or the `--!>` terminator. So `<!-->` followed by a `<script>` block
was reported clean, while both markdown parsers in this system render it as
raw HTML and a browser executes it. Five such bodies passed the gate.

**The library's URL policy is not a browser's URL parser.** A character
reference without its terminating semicolon — `[x](&#106avascript:alert(1))`
— is not a character reference to a CommonMark parser, so the destination is
plain text and the policy reports nothing. The client's renderer emits the
undecoded string into `href`, and the browser resolves `&#106` to `j` while
parsing the attribute. The same body with the semicolon present is correctly
refused, which is exactly what makes the omission easy to miss.

Neither finding is a disagreement between the two markdown parsers. Both are
a disagreement between a library's lexer and a browser's, which is why the
answer cannot be another library setting:

1. **Refuse raw markup constructs on the parse tree.** Any `HtmlBlock` or
   `HtmlInline` whose literal contains `<!` or `<?` is refused outright:
   comments, CDATA sections, doctypes and processing instructions. A lesson
   or a blog post has no use for any of them, and refusing them by shape
   needs no lexer to agree with anything.
2. **Resolve link and image destinations the way a browser does.** Decode
   character references with or without the trailing semicolon, strip C0
   controls and whitespace, lowercase, then require `http`, `https`, `mailto`
   or a relative path.
3. **Then** run the allow-list for its verdict, for elements and attributes.

The parser's own limits are pinned as part of the same gate. The version
first proposed here had quadratic behaviour on repeated `<!--` and overflowed
the stack on deeply nested emphasis or images; a body within the documented
200,000 character limit reached seconds of CPU or an unhandled error. The
current version, with explicit nesting limits, answers the same inputs in
milliseconds.

### The pipeline may not throw

A draft the pipeline generates is validated like any other write, and it can
legitimately fail: a release note that quotes `[click me](javascript:alert(1))`
while describing the vulnerability it fixes is ordinary content. Raising from
inside the item's transaction rolled back the item's own audit trail, skipped
every remaining item in that feed, and left the source silently stalled — the
next cycle found nothing deduplicated and struck the same item again.

Draft validation is therefore a verification check like the others
(`DRAFT_VALIDATION`): a failing draft rejects its item, records why, and the
feed continues. Each item is additionally isolated, so no single item can end
a cycle.

### The excerpt is the one place a body is not the author's bytes

The pipeline's draft template embeds a sanitized excerpt of the feed entry,
and that excerpt really is the sanitizer's output — entity-encoded characters
included. An administrator editing such a draft sees those entities in the
generated part. This is the single documented exception to "the stored form
is the source"; it applies only to machine-generated drafts, and re-saving
one stores exactly what the editor then contains.

Truncating that excerpt to a fixed length used to cut a tag in half, and the
unclosed tag swallowed the rest of the template — including the source link
the contract requires on an automatic post, so the draft could not pass its
own validator. Eleven of twenty real release entries were refused this way.
The excerpt is sanitized again after truncation: the policy's output is a
fixed point of the validator, so a second pass is enough.

### Already-stored content

Forward-only. No migration decodes existing rows, for three reasons:

1. There is no deployed database. The seed content never crossed the write
   boundary and is unaffected; development and test databases are
   disposable.
2. A decode is not an inverse. The nine-character entity rewrite could be
   reversed with a fixed replacement chain (`&amp;` last), but stripped
   markup (`<details>` → `Morehidden`) is gone, and an author who literally
   wrote `&#96;` is indistinguishable from one who wrote a backtick.
3. The correct repair path already exists: an affected row is re-saved
   through the editor, which crosses the fixed write boundary, bumps the
   content version and repackages through the service layer like any other
   edit. Affected rows are enumerable for any database that matters:

   ```sql
   SELECT id, slug FROM lessons
    WHERE body_markdown ~ '&#(34|39|43|61|64|96);|&(lt|gt);';
   ```

   (`&amp;` is omitted from the pattern because an author may legitimately
   write it in raw HTML; a listing that over-reports is fine, a fix that
   over-corrects is not.)

### Content digests and versions

Unaffected. The digest is a function of the stored bytes and remains one;
this decision changes what bytes a *new* write stores, not how any bytes are
hashed. Every existing package is still consistent with its stored `sha256`,
so nothing is cleared or recomputed, and no `content_version` moves until a
human edits the row — at which point it moves for the ordinary reason. The
determinism test gains one case: a body containing backticks, `=`, `'` and a
blockquote is created through the API, read back, and packaged
byte-identical to its normalized input.

## Consequences

### Positive

- Authored content is stored exactly as written (after line-ending and NFC
  normalization). Code fences, inline code, blockquotes, apostrophes and
  e-mail addresses survive.
- Unsafe markup is refused with a reason at the point of authoring, in the
  editor, instead of being rewritten into something the author never sees
  until it renders.
- The server-side allow-list becomes a stated, testable contract (the
  elements and attributes a body may carry) rather than an emergent property
  of a library's default policies.

### Negative

- One new server dependency (`commonmark`, 218 KB, no transitive
  dependencies).
- Two CommonMark implementations in the system (server validation, client
  rendering). Their disagreements are the client sanitizer's job, and that
  layer must stay — this decision makes it more load-bearing, not less.
- Authors pasting HTML-heavy content from elsewhere will hit refusals that
  the previous behaviour hid. The `422` payload names the elements, which is
  the information needed to fix the paste.

### Follow-up

- `docs/protocol/rest-api.md`: rewrite §2.8; retire `SANITIZED_CONTENT_EMPTY`
  (a body can no longer become empty through the write boundary — blankness is
  ordinary validation) and add `UNSAFE_HTML` (422) to the error table and to
  the error lists of the lesson, blog, translation and mind-map write
  endpoints; correct the sentences at the lesson response ("the sanitized
  stored text, which may differ from what was sent") and at the source-update
  detail ("`raw_content` is the sanitized stored source text" — it is the
  normalized stored source text).
- Server: `MarkdownSanitizer` becomes a validator with one shared policy
  (`validateMarkdown`, `validatePlainText`) plus one transformer
  (`sanitizeHtml`) used only by the pipeline's draft template on the feed
  excerpt; `AdminLessonService`, `AdminBlogPostService`,
  `TranslationService` and the mind-map path call the validator; the
  pipeline's `CONTENT_SANITY` check uses the transformer's non-blank output
  as it does today.
- Tests: the existing sanitization cases in `AdminLessonCodeExampleIT`
  (script stripped; script-only body → `SANITIZED_CONTENT_EMPTY`) become
  refusals (`UNSAFE_HTML`); new cases for the round-trip above, for a fenced
  `<script>` that passes, for a `javascript:` markdown link that fails, and
  for a mind-map label `a <b` that fails while `a < b` passes.
- Frontend: the error-code translation key for `SANITIZED_CONTENT_EMPTY` is
  replaced by one for `UNSAFE_HTML` in all four locales.
- The allow-list is frozen only after being measured against the seed lessons
  and a sample of real release-notes HTML from the whitelisted feeds, and the
  measured list is recorded in the contract.
