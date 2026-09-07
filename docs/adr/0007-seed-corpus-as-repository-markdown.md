# 0007. The seed corpus lives as repository markdown, loaded by a migration

## Status

Accepted

## Context

The application is finished and nearly empty. Its whole corpus is the sample
seed from an early phase — one track, two modules, four lessons and one mind
map, written as escaped string literals inside `V5__seed_content.sql`. That was
the right size for exercising the read endpoints, the manifest and the desktop
client against something real before any authoring surface existed. It is not a
learning platform.

Filling it raises a question the sample never had to answer, because four
lessons can be read in one sitting and ninety cannot: **how does content get in,
and what stops bad content from getting in with it?**

Three constraints shape the answer. Each was measured, not assumed.

**There is no authoring interface for teaching content.** The administration
routes cover blog posts, the review queue and whitelist sources. Tracks,
modules, lessons and mind maps have no editor at all; they exist only as rows
the sample migration inserted.

**The write boundary that protects blog content does not protect seed content.**
ADR-0002 records how markdown arriving through the administration API is
validated and refused when unsafe, and why that boundary rejects rather than
rewrites. Seed SQL goes nowhere near it. A bulk corpus written as `INSERT`
statements could carry markdown the application itself would refuse, and nothing
would notice — that ADR says as much in passing, observing that seed content had
never crossed the boundary.

**Content has no test that tells you it is wrong.** Code that is wrong fails a
compiler or a test. A lesson that teaches the wrong thing renders perfectly,
digests to a stable hash, and is indistinguishable from a correct one to every
mechanism in this system. The only instrument that detects it is a person
reading it, and reading time is the scarcest resource here.

That last constraint decides the shape. Whatever the mechanism, it has to make
the corpus **reviewable**, and it has to make approval **explicit and
attributable** — because this project already holds, in `I2`, that automatically
produced content is not published without a human saying so, and there is no
principled reason a lesson deserves less care than a blog post.

## Options considered

### A. Hand-written SQL, extending the existing seed migration

More `INSERT` statements, bodies as `E'...\n...'` escaped strings.

- **For:** identical to what exists; one file; no new machinery. Identifiers and
  timestamps stay fixed literals, so a developer machine, continuous integration
  and a fresh deployment address the same rows.
- **Against:** ninety lessons of escaped SQL cannot be reviewed. The reviewer
  would hunt for a teaching error through a layer of backslash escapes, in a
  file where prose and schema interleave. And the write boundary stays bypassed
  — the exact hazard this decision exists to close.

### B. Markdown files in the repository, loaded by a Java migration

The corpus lives as ordinary files: one markdown body per lesson, metadata
beside it, code listings as real source files. A `BaseJavaMigration` reads the
tree, normalises and validates every value through the same predicates the
administration API calls, and upserts rows by explicit identifier.

- **For:** a lesson is reviewed as a lesson, in a diff that shows prose as prose.
  Code listings are files a compiler can read, which is what makes the one
  mechanically checkable claim — that the examples compile — checkable at all.
  The validation predicates are `public static` and need no HTTP context, so the
  boundary is **moved rather than bypassed**: content the API would refuse fails
  the migration, which fails startup, which fails the build.
- **Against:** a new loader to write and to trust. Java migrations carry a
  checksum, so editing a loaded file afterwards invalidates it and the local
  database must be reset during a review cycle.

### C. Posting the corpus through the administration API

A script that authenticates and posts each lesson.

- **For:** the boundary is crossed exactly as a human author would cross it, with
  no new code path to trust.
- **Against:** identifiers would be minted per environment, so a developer
  machine, continuous integration and a deployment would stop agreeing on which
  row is which — the property the sample seed's fixed literals exist to
  preserve. Integration tests would not see the corpus at all, since it would
  not be in the database until someone ran the script against a live server with
  an administrator account. And there is no endpoint for tracks, modules or mind
  maps to post to.

### D. Loading at startup from a runner rather than a migration

- **For:** no checksum, so editing during review costs nothing.
- **Against:** the database's contents stop being a function of its migration
  history. What a deployment holds would depend on which build last started
  against it — the property migrations exist to remove.

## Decision

**Option B.** The corpus is repository markdown; a Java migration loads it.

The loader normalises every body and every code listing, then validates them
with the same static predicates the administration API uses: the markdown
validator for bodies, the plain-text validator for mind map labels, and the
closed language list for code listings. A value that would be refused over HTTP
is refused here, and the refusal fails the migration rather than being logged
and skipped.

Rows are upserted by identifier from fixed blocks assigned per track, so the
sample seed's guarantee survives: every environment addresses the same row. When
a body changes, the loader increments the content version and clears the three
package columns. It never computes a digest itself, leaving that to the
repackaging that already runs at startup — writing a digest in a migration is
precisely what `I1` and its trap note forbid.

**Every track is loaded unpublished.** The public read paths serve only
published tracks, so a freshly loaded corpus is invisible until someone changes
the flag. Review happens against the running application with the flag flipped
locally; the commit that sets it to `true` in the repository is the approval,
and version control attributes it to a person. That is `I2` applied to teaching
content, implemented as a mechanism rather than promised as a practice.

A second layer runs in the test suite: an integration test re-applies the same
predicates to **every** seeded row after the whole migration chain has run —
including the rows the original sample seed inserted, which nothing has ever
validated.

## Consequences

**Positive.** A lesson is reviewable as prose. Code listings are files, so "the
examples compile" becomes a command with output rather than a claim. The write
boundary covers seed content for the first time, and covers the existing sample
rows as a side effect. Approval is a commit, not an assurance.

**Negative.** A Java migration is checksummed, so correcting a loaded lesson
during review means resetting the local database rather than editing in place;
the review loop pays a real cost per iteration. The alternative — a loader
without a checksum — trades that cost for silent drift between what the
repository says and what a database holds, which is worse. Corrections after a
corpus ships require a new migration and a new corpus directory holding only the
changed files.

**Accepted risk.** The loader is new code on the path that fills the product. It
is covered by tests that assert it *bites*: unsafe markdown, an unlisted code
language and a malformed mind map must each fail the migration rather than pass
quietly. A validation layer never observed refusing anything is
indistinguishable from one that does nothing.

**Unchanged.** Digests, packaging and the manifest are untouched. The loader
writes rows and clears package columns; the existing repackaging fills them. The
corpus adds roughly a megabyte of packaged content, well inside the protocol's
per-package ceiling.
