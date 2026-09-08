# Content Corpus Format

**Status: frozen.** This document defines how teaching content lives in the
repository and what the loader may accept. ADR-0007 records why the corpus is
repository markdown loaded by a migration rather than SQL, an API script or a
startup runner; this is the shape that decision implies.

Not covered here: the REST surface (`rest-api.md`), the manifest and packaging
rules (`content-sync.md`), or the platform abstraction (`platform-service.md`).
This document consumes all three.

---

## 1. What a corpus is

A corpus is one directory per track under
`server/src/main/resources/content/<version>/`, read from the classpath by the
migration that loads it. A version directory is written once and never edited
after its migration has run anywhere: a Java migration carries a checksum, and
editing a loaded file invalidates it. Corrections after a corpus ships are a new
version directory holding **only the changed files**, loaded by a new migration.

```
content/v13/
  typescript-path/
    track.json
    lessons/
      what-a-type-really-is.md
      what-a-type-really-is.json
      ...
    examples/
      what-a-type-really-is-1.ts
      what-a-type-really-is-1.expected
      ...
```

Three kinds of file, and the loader treats them differently:

| File | Loaded into the database | Read by the verification runner |
|---|---|---|
| `track.json`, `lessons/*.json`, `lessons/*.md` | yes | no |
| `examples/*.<ext>` | yes, as a code example | yes, compiled and run |
| `examples/*.expected` | **no** | yes, compared against the run's output |

**The loader reads the classpath, not the source tree.** A corpus file deleted
from `src/main/resources` stays in `target/classes` until something rebuilds
the resources, and the loader will keep finding it — including scratch files an
author left behind and then removed. A load that refuses a file you cannot see
is this, and the fix is a rebuild rather than a hunt.

The `.expected` files are evidence, not product. A loader that cannot tell the
two apart would store them as lessons; the extension table in section 5 is what
keeps them apart, and anything not in it is a loader error rather than a
silently skipped file.

---

## 2. `track.json`

```json
{
  "id": "019205a0-1000-7000-8000-000000000003",
  "slug": "typescript-path",
  "title": "The TypeScript Path",
  "description": "One sentence, no more than 200 characters.",
  "icon": null,
  "order": 3,
  "teaches_version": "TypeScript 5.9",
  "modules": [
    {
      "id": "019205a0-2000-7000-8000-000000000301",
      "title": "Foundations",
      "order": 0,
      "estimated_minutes": 15,
      "lessons": ["what-a-type-really-is", "..."]
    }
  ]
}
```

`teaches_version` is required and is not decoration. A track teaches one pinned
version and says so, because an unversioned claim cannot be checked against
anything — see section 8. It is stored in the track description's trailing
sentence rather than a new column, so no migration to the schema is needed.

**A track's `order` and a module's `order` follow different rules.** They look
alike and they are not, and an author who applies one to the other fails the
load — which has now happened.

- **A track's `order` is its number from the plan.** Track `TT = 03` has
  `order: 3`. One-based, because the loader refuses anything below one, and
  tied to the track number so that two authors working in parallel cannot
  choose the same value.
- **A module's `order` is its zero-based position in the `modules` array.**
  The first module is `0`. The loader derives the module's identifier from that
  position, so a declared order that disagrees with the position would produce
  an identifier nobody could predict; it refuses instead.

`published` does not appear in this file. See section 7.

A lesson slug listed under `modules[].lessons` must have both a `.md` and a
`.json` in `lessons/`, and its order within the module is its position in that
array.

**Slugs are unique across the whole corpus, not per track**, and track slugs
share that one namespace with lesson slugs. The loader claims every slug it sees
into a single map and fails on the second claim, so two tracks that each want
`testing-a-component` collide, and the second one to load takes the blame for a
name the first one used. This is the collision that parallel authoring makes
likely rather than theoretical: almost every track ends with a lesson about
testing, and the natural name for it is the same in every language. Qualify it
— `testing-and-what-cargo-test-runs`, `testing-without-a-browser` — rather than
discovering the clash at load time.

---

## 3. `lessons/<slug>.json` and `lessons/<slug>.md`

```json
{
  "id": "019205a0-3000-7000-8000-000000000301",
  "title": "What a type really is",
  "difficulty": "BEGINNER",
  "estimated_minutes": 5,
  "sources": ["https://www.typescriptlang.org/docs/handbook/2/everyday-types.html"],
  "concepts": ["structural typing", "type inference"],
  "code_examples": [
    { "language": "typescript", "caption": "Structural compatibility", "file": "what-a-type-really-is-1.ts" }
  ]
}
```

A listing carries **no identifier of its own**. The loader derives it from the
lesson's block and the listing's position in the `code_examples` array — the
`EE` component of §4 is that index, one-based. Reordering the array therefore
renumbers the listings, which is harmless because nothing outside the lesson
refers to them.

`difficulty` is one of `BEGINNER`, `INTERMEDIATE`, `ADVANCED`. `concepts` are
the labels this lesson contributes to the mind map (section 6); zero to two per
lesson, each a plain-text phrase.

The `.md` file holds the body and nothing else — no front matter, no title
heading. The title lives in the JSON, and a body that repeats it produces two
headings on the rendered page.

**Markdown tables render.** An author asked, having found no corpus lesson using
one and no statement either way, and rewrote a table as a list rather than risk
it. That caution cost a better presentation for nothing: the reader renders
bodies with `marked`, which emits GFM tables, and the server's write-boundary
policy carries `Sanitizers.TABLES`, so table elements survive validation. Use a
table where a table is the honest shape — a comparison across three or four
cases usually is — and keep it narrow enough to read on a phone.

### 3.1 The lesson shape

Every lesson follows the same six-part shape, in this order. The shape is fixed
across the whole corpus so that a reader learns it once, an author cannot
improvise a structure, and a reviewer knows where to look.

1. **Why this exists** — the problem it solves, one paragraph.
2. **The idea** — one analogy, followed by a subsection headed **Where the
   analogy breaks** that says what the analogy gets wrong.
3. **How it works** — the mechanism, with fenced code inline.
4. **Common mistakes** — only mistakes that produce a real diagnostic: a
   compiler error, a runtime failure, a wrong output that can be demonstrated.
5. **Check yourself** — two or three questions, each answer inside a
   `<details><summary>` block.
6. Full listings, referenced from `code_examples`.

**One analogy per lesson, and never more.** An analogy is the most effective
teaching device available and the one that leaks the most: a reader carries it
past the point where it holds. The "where it breaks" subsection is not optional
padding — it is the repair, it is the shortest high-value thing a reviewer
reads, and a lesson without it does not pass review.

### 3.2 Limits

| | |
|---|---|
| Body | 450 words minimum, 700 target, 1000 maximum, excluding fenced code |
| Inline fences showing code | 1 to 4 |
| Inline fences quoting observed tool output | not capped |
| Full listings | 2 to 4 per lesson |
| Lessons per module | exactly 3 |
| Modules per track | exactly 3 |

The floor is one, not two, and the reason is a mistake this document already
caused. An earlier floor of two sent an author looking for a second code block
in all nine lessons of a track — every one of them landed somewhere useful, but
by the author's own account that was luck, and a limit whose failure mode is
*manufacturing content to satisfy a count* is worse than no limit. The floor
exists only to catch a lesson about code that shows none; one block that earns
its place satisfies it.

The two kinds of inline fence are counted separately, and the reason is worth
stating because the distinction is easy to read as a loophole. The cap exists to
stop a body from becoming a code dump with sentences between the blocks — that
hazard belongs to fences the author *wrote*. A fence quoting what a tool *said*
— a compiler diagnostic, a stack trace, a query plan, a failing assertion — is
not a substitute for prose but the evidence prose is making a claim about, and
§3.1 requires that evidence: a common mistake counts only if it produced a real
diagnostic the author observed. Capping quoted output at four would push a
track with three honest diagnostics per lesson toward describing them from
memory instead of showing them, which is the opposite of what the cap is for.

Quoted output is held to a different standard in exchange: it is pasted from a
run, never reconstructed, and §9's rule on unstable output applies to it exactly
as it applies to a recorded `.expected` file. A diagnostic quoted with its
machine's paths, a duration, or a thread identifier is an artefact, not a
property.

The loader enforces the word count and the listing ceiling; it does not count
fences. That is deliberate — telling a code fence from a quoted diagnostic
needs a reader, not a parser — and it means this limit is held by review rather
than by the migration failing.

A lesson that cannot reach 450 words without padding has nothing to teach and
is dropped. A track that cannot fill nine lessons is not shipped; it waits for a
later corpus version rather than shipping thin.

---

## 4. Identifiers

Identifiers are fixed literals, not generated values, so a developer machine,
continuous integration and a deployment all address the same row. They are
UUIDv7-shaped — version nibble 7, variant bits `10xx` — matching every
identifier the application mints at runtime.

Blocks are allocated per track, with **thirty-two** reserved so that adding a
track later never means reallocating one that shipped:

```
track        019205a0-1000-7000-8000-0000000000TT
module       019205a0-2000-7000-8000-00000000TTMM
lesson       019205a0-3000-7000-8000-000000TTMMLL
mind map     019205a0-4000-7000-8000-0000000000TT
code example 019205a0-5000-7000-8000-0000TTMMLLEE
```

`TT` is the track number `01`–`32`, `MM` the module `01`–`03`, `LL` the lesson
`01`–`03` within its module, `EE` the listing `01`–`04` within its lesson.

**The Angular track is an exception, and it stays one.** Its rows were seeded
before this scheme existed and use plain sequential suffixes. They keep them.
Renumbering them would not be a tidying exercise: progress rows on every
installed client are keyed by lesson identifier, and changing an identifier
orphans a reader's completion silently. The exception is recorded here so the
next person does not "fix" it.

---

## 5. Extensions and languages

The loader maps a listing's extension to the language identifier stored with the
code example. The set of language identifiers is closed and defined by §5.4.5 of
the REST contract; this table maps into it and may not introduce a value that is
not in it.

| Extension | Language | Extension | Language |
|---|---|---|---|
| `.ts` | `typescript` | `.go` | `go` |
| `.js` | `javascript` | `.rb` | `ruby` |
| `.java` | `java` | `.php` | `php` |
| `.kt` | `kotlin` | `.cs` | `csharp` |
| `.py` | `python` | `.cpp` | `cpp` |
| `.rs` | `rust` | `.sql` | `sql` |
| `.sh` | `bash` | `.html` | `html` |
| `.css` | `css` | `.json` | `json` |
| `.yaml`, `.yml` | `yaml` | `.xml` | `xml` |
| `.txt` | `text` | `.expected` | *not loaded* |

A component template — Angular, Vue, Django — is stored as `html`. There is no
`vue` or `jinja` identifier in the closed set and this document does not add
one; a template that needs highlighting the set cannot provide is a reason to
change the set deliberately, not to smuggle a value past it.

**But a template is rarely the right thing to make a listing out of.** §9
requires every listing to be executed and its output recorded, and a template
cannot be executed on its own — it needs the framework that renders it. So a
template belongs in the body as an inline fence, where it illustrates without
claiming to have been verified, and the listing beside it is the runnable file
that renders the template and prints what came out. Where a track genuinely
needs a template as a listing, its report says the file was not executed and
why, exactly as it would for any other unrunnable listing.

An extension absent from this table fails the load. Silently skipping an unknown
file is how a listing goes missing without anyone noticing.

---

## 6. Mind maps

One per track, **derived from the track's structure rather than authored**. The
root is the track, its children are the modules, each module's children are its
lessons, and a lesson's `concepts` become leaves beneath it.

A hand-drawn map is a second thing to keep in step with the lessons and a second
thing to review; a derived map cannot drift, because there is nothing to drift
from. The cost is that a map says exactly what the structure says, which for a
nine-lesson track is the honest amount.

Between thirteen and thirty-one nodes per track: one root, three modules, nine
lessons, and nought to two concept leaves per lesson. The protocol's ceilings —
five hundred nodes, depth eight — are far above this, and the shape here is
depth four.

Mind map labels are not translated in this version, by an existing recorded
decision. Every label is plain text and is validated as such.

---

## 7. Publication is a separate migration

The loader writes every track **unpublished**, and on a later run it does not
touch the `published` column at all. Publication is a one-line migration per
track:

```sql
UPDATE tracks SET published = true WHERE id = '019205a0-1000-7000-8000-0000000000TT';
```

This is an amendment to ADR-0007, which described publication as flipping a flag
in the corpus metadata. That would have worked, but it would change a file the
loader checksums, so every publication would invalidate the migration and force
a local database reset. Separating them costs nothing and keeps what matters:
the commit that publishes a track is attributable to a person, which is how
`I2` — nothing automatically produced is published without a human saying so —
applies to teaching content.

The public read paths serve only published tracks, so an unreviewed corpus is
invisible in the product while it is being reviewed.

---

## 8. Sources

Every mechanism claim is anchored to official, versioned documentation, listed
in the lesson's `sources`. The allow-list of hosts:

```
angular.dev            docs.spring.io          docs.oracle.com
typescriptlang.org     developer.mozilla.org   docs.python.org
doc.rust-lang.org      go.dev                  kotlinlang.org
learn.microsoft.com    www.php.net             ruby-doc.org
docs.djangoproject.com vuejs.org               react.dev
nodejs.org             www.postgresql.org      en.cppreference.com
```

A source outside this list fails review. Adding a host is a decision recorded
here, not something an author does while writing.

**Subdomains of a listed host are allowed**, because the loader matches a host
by suffix. `router.vuejs.org` and `docs.djangoproject.com` are inside the list
without needing their own line, and where a project splits its documentation
across subdomains the one that actually documents the mechanism is the right
citation — a framework's guide is not more authoritative than the library's own
reference merely because it lives at the shorter address.

**A blog post is not a source, even on a listed host.** Release announcements
and engineering posts sit at `vuejs.org`, `go.dev` and `react.dev` alongside the
reference documentation, and they are tempting because they explain a change in
prose. They are excluded anyway: a post is written once and describes the world
on its publication date, so a claim anchored to one cannot be re-checked when
the behaviour changes, and the reader has no way to tell that it went stale.
Anchor to the reference or guide page that carries the same fact — it will
almost always state it with a sharper boundary, since that is what reference
pages are for. Where only a post carries a number (a benchmark, a memory
figure), the number does not go in the lesson at all.

This rule already lived in the loader's own commentary and not here, which is
how one author came to discover it by reading the source. It is written down
now.

**Forbidden in a body:** the words `latest`, `currently`, `as of writing`, and
any version-free claim about behaviour. A reader cannot tell when a lesson was
written; a lesson that depends on when it was written is already wrong.

---

## 9. What proves a listing

A listing is not evidence because it looks right. Every full listing is
compiled **and run**, and its output compared against its `.expected` file. The
commands, per language, on the machine this corpus is authored on:

| Language | Command |
|---|---|
| TypeScript | See §9.1 — the naive command does not work here |
| JavaScript, Node | `node <file>` |
| Java | `javac --release 21` then `java` |
| Kotlin | `%LOCALAPPDATA%\kotlin\kotlinc\bin\kotlinc.bat` — an absolute path; it is not on `PATH`, and a command that assumes it is leaves the track silently unverified. Coroutines need no installation: `kotlinx-coroutines-core-jvm.jar` ships inside that distribution's own `lib/`, so pass it on `-cp` rather than marking a coroutines listing unrunnable |
| Python, Django | `python <file>` |
| Rust | `rustc --edition 2024` then run |
| Go | `go run <file>`; for the race detector `CC=clang CGO_ENABLED=1 go run -race <file>` — plain `-race` fails here twice over, first because it needs cgo and then because `gcc` is not installed, and LLVM's `clang` is what this machine has |
| Ruby | `C:\Ruby34-x64\bin\ruby.exe <file>` — an absolute path; Ruby is not on `PATH` here |
uby.exe <file>` — an absolute path; Ruby is not on `PATH` here |
| PHP | `php <file>` |
| C# | in a scratch project, `dotnet build -v q --nologo` then `dotnet run --no-build` — a plain `dotnet run` prints restore and build lines to stdout, and they would end up in the recorded output |
| C++ | `"C:\Program Files\LLVM\bin\clang++.exe" -std=c++23 -Wall -Wextra -Wpedantic -Werror` **and** a run under `-fsanitize=address,undefined -fno-sanitize-recover=undefined` — an absolute path; `clang++` is not on `PATH` here. See below for why the recover flag is not optional and what these sanitizers do not catch on this machine |
| SQL | executed against Postgres 16 in Docker |

**Expected output must be stable**, and this is the rule listings break most.

A runtime's own phrasing — a parse error, a platform name, a stack frame —
changes between versions and turns a listing into a test of the machine it ran
on. **A library's phrasing counts as the runtime's**, and the standard library
is where this is easiest to miss: the body a web framework writes for a 404, the
text a JSON decoder puts in an error, the way a collection formats itself. Print
the status code, not the page it came with.

There is one defensible line inside that rule, and an author who draws it says
so in the listing. Text produced by the **language engine itself** — a
`TypeError` from the interpreter, a compiler-generated `toString`, an
exception the language specification defines — is pinned by the version the
track already declares, and recording it is recording the version. Text
produced by something that merely **ships alongside** the engine — a database
driver's message, an image library's error — is not pinned by that version at
all, and goes out. When in doubt, record the code and not the sentence: a
`SQLSTATE`, an error number, an exception's type name.

**Never write a platform newline constant into recorded output.** `PHP_EOL`,
`System.lineSeparator()` and `Environment.NewLine` are `
` on this machine,
which puts carriage returns into an `.expected` file that every other rule
assumes is LF. Write `
`. So does anything a language declines to specify: hash map iteration order
is unspecified in Java, and Go deliberately randomises it on every run, so a
listing that prints a map's contents records an accident.

The fix is not to sort the output when the lesson is *about* the disorder. It
is to **print the property rather than the artefact**. A lesson teaching that a
hash map does not preserve insertion order demonstrates it with

```
insertion order preserved: false
```

and not by pasting the order that one run happened to produce. The first is the
claim being taught and is true on every machine; the second is a snapshot that
will go red on a version upgrade for a reason that has nothing to do with the
lesson.

Where order genuinely is part of the lesson and genuinely is specified — a
sorted map, an ordered collection — printing it is correct, because the
specification is what makes it reproducible.

**Pin the locale wherever a listing formats a number or a date.** A decimal
separator, a thousands separator, a month name and a date order all come from
the machine's culture unless the listing says otherwise, so the same program
records `2.5` on one machine and `2,5` on another. Three listings in one track
were rewritten for exactly this after the recorded output turned out to describe
the authoring machine rather than the language. Set the invariant culture, or
format explicitly with a culture-independent pattern, and prefer the latter when
the lesson is about formatting itself.

**Non-ASCII in recorded output must be the lesson, not decoration.** Recorded
output travels through a console code page on the way to the file, and a code
page that cannot represent a character silently substitutes another — one track
found its em dashes arriving as `?`. An em dash or a typographic ellipsis in a
program's output buys nothing and can only cost, so write those in ASCII. Text
that *is* the lesson stays: a listing teaching that `len("grün")` is five bytes
and four runes must print `grün`, and the author's job there is to confirm the
bytes survived the round trip rather than to avoid them.

**"It compiles" is not the bar.** A listing that compiles and produces the wrong
answer is exactly the failure a reader cannot detect, which is why the expected
output is part of the corpus and the comparison is part of the evidence.

**A listing that could not be run says so, in the tree.** Some tracks teach a
framework this repository does not carry and may not install. Such a track can
still be authored honestly — most of what a framework exposes is language
semantics that can be modelled and run — but a few listings will be framework
code that no command here can execute.

The marker for that is the **absence of the `.expected` file**, and it is
unambiguous precisely because a listing that runs and prints nothing gets an
empty `.expected` rather than none. So:

- A listing **with** an `.expected` file was executed and its output compared.
- A listing **without** one was not executed, and no claim is made about it.

Two things follow, and both are required rather than encouraged. The listing
itself opens with a comment saying it was not executed and naming the reason —
the file has to carry its own provenance, because whoever reads it next will
have neither the authoring report nor this document open. And the lesson body
must not imply the listing ran: it may present the code as correct and
idiomatic, but not as verified.

This is the whole mechanism. There is no metadata field for it, because a field
would be a second place to state a fact the tree already states, and the two
would drift.

### 9.1 Type-checking TypeScript listings

Two authors independently discovered that the obvious command does not work,
and both invented the same workaround. It belongs here rather than being
rediscovered a third time.

`npx tsc --strict --noEmit <file>` fails in this repository for three reasons
that compound:

1. Naming a file on the command line while a `tsconfig.json` is present is an
   error (`TS5112`) in the TypeScript the frontend carries.
2. Without a target the compiler defaults to ES5, so `Map`, `Promise`,
   `Object.entries` and `Array.prototype.find` do not resolve — which does not
   fail loudly, it just pushes an author into writing listings nobody would
   write.
3. A listing that imports from `@angular/*` needs module resolution that
   reaches the frontend's installed packages, and the corpus directory has no
   `node_modules` above it.

So verification uses a **scratch tsconfig outside the repository**, and nothing
is written into the repository to support it:

```json
{
  "compilerOptions": {
    "strict": true, "noEmit": true,
    "target": "ES2022", "lib": ["ES2022"],
    "module": "preserve", "moduleResolution": "bundler",
    "skipLibCheck": true,
    "paths": { "@angular/*": ["<repo>/frontend/node_modules/@angular/*"] }
  },
  "files": ["<the one listing under test>"]
}
```

One listing per invocation, `npx tsc -p <that file>`, run from `frontend/`.
A listing that is plain TypeScript is then also executed with `npx tsx` and its
output recorded; one that imports Angular is type-checked only, and the report
says so per listing.

**Inline component templates are not checked by this.** Template type checking
is the Angular compiler's job and needs a real build. A track whose listings
carry templates has that one unverified layer, and its report must name it
rather than let the green type-check imply more than it proves.

C++ carries the extra sanitizer requirement because compilation proves less
there than anywhere else: undefined behaviour compiles. If the sanitizers turn
out to be unavailable on this platform, that is measured and reported, and the
C++ listings are verified on a Linux runner instead — not waived.

`-fno-sanitize-recover=undefined` is part of that requirement rather than a
refinement of it. The undefined-behaviour sanitizer's default is to print a
report and **carry on, exiting zero**, so a listing can violate the language,
say so on stderr, and still be recorded as a clean run by anything that looks at
the exit code. With the flag the violation ends the process. Check stderr is
empty as well: two instruments are cheap, and each catches what the other lets
through.

What these sanitizers do **not** catch here was measured, and is written down
because a sanitizer believed to be complete is worse than one known to have a
hole:

- **A `std::vector` index inside `capacity()` but past `size()`.** The MSVC
  standard library needs `stl_asan.lib` for its container annotations and only
  the x86 copy is installed, so the C++ listings build with
  `-D_DISABLE_VECTOR_ANNOTATION -D_DISABLE_STRING_ANNOTATION`. Measured: after
  `reserve(16)` with one element, reading index 9 ran clean and printed
  rubbish. The allocation is real, so the heap sanitizer has nothing to object
  to.
- **Leaks.** `ASAN_OPTIONS=detect_leaks=1` answers `detect_leaks is not
  supported on this platform`. A lesson about a reference cycle therefore
  proves the leak with a destructor that does not run, not with a leak report —
  which is the better evidence anyway, since a leak report is a fact about a
  tool's configuration and a silent destructor is a fact about the program.
- **Strict-aliasing violations**, which the sanitizer does not diagnose in
  general.

A lesson that would rely on one of these must say what is not being checked
rather than implying the sanitized run covers it.

---

## 10. What the loader must refuse

Refusal fails the migration, which fails startup, which fails the build. Nothing
here is a warning.

1. A body, caption or label that the markdown or plain-text validator rejects —
   the same static predicates the administration API calls, so that seed content
   crosses the same boundary written content does.
2. A listing whose extension is not in section 5, or whose mapped language is
   not in the closed set.
3. A lesson slug listed in `track.json` with no `.md` or no `.json`, or a lesson
   file that no `track.json` lists.
4. A duplicate identifier, a duplicate slug, or an identifier outside the
   track's allocated block.
5. A body outside the word limits of section 3.2, or a track without exactly
   three modules of exactly three lessons.
6. A `sources` entry whose host is not in section 8.
7. A mind map that repeats a node, exceeds the depth, or references a lesson
   outside its track.

The loader is covered by tests that assert it **bites**: unsafe markdown, an
unlisted language and a malformed mind map each fail the load. A validation
layer never observed refusing anything is indistinguishable from one that does
nothing.

Normalisation happens before validation and before storage — UTF-8, LF line
endings, NFC — so that a checkout on a machine with different line-ending
settings produces the same stored bytes and therefore the same digest. The
loader never computes a digest itself; packaging at startup does that.
