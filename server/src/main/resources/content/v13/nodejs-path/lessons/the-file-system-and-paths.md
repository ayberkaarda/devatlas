## Why this exists

A path bug is a deployment bug. It is written on a laptop, it passes every test on that laptop, and
it fails on the machine that has a different separator, a different case sensitivity or a different
working directory. Node.js 22 gives you `node:path` so you never have to know which of those you
are on, and `node:fs/promises` so file work joins the rest of your asynchronous code. Both are
easy to use in a way that looks right and is not: a path glued together with `'/'`, or an `fs` call
whose promise nobody awaited, both behave perfectly until the day they do not.

## The idea

`node:path` is arithmetic on strings, like working out a route on paper. It never touches the disk.
`path.join` glues fragments and tidies the result; `path.resolve` walks right to left until it has an
absolute address; `path.normalize` collapses `..` on the page.

### Where the analogy breaks

Paper arithmetic is happy to name a place that does not exist, and `path` is too: every function
here answers without asking the file system anything, so a beautifully normalised path can point at
nothing at all.

Worse, the paper `..` and the real `..` are not the same move. `path.resolve` collapses `..`
textually, but on disk a symbolic link makes `a/b/..` a different directory from `a`. The function
that asks the file system is `fs.realpath`, and it is the one to use when the answer has to be true
rather than merely well-formed.

The arithmetic is also platform-flavoured. `path` is whichever implementation the running platform
needs, and `path.win32` and `path.posix` are always available if you want a specific one — which
is what a test that has to produce the same text everywhere should use.

## How it works

Build paths with `join` and `resolve`, never with `+`. Windows accepts forward slashes, so
concatenation appears to work and then fails on the first `..` or double separator.

```js
path.join('data', 'logs', 'app.log');   // separators for this platform
path.resolve(root, userSegment);        // absolute, and rooted wherever the segment says
```

The difference between the two is where path traversal lives. `join` treats a leading separator as
an ordinary segment; `resolve` treats it as a new root. Neither one contains a caller's input on its
own, so containment has to be checked explicitly.

```js
const full = path.resolve(root, candidate);
const rel = path.relative(root, full);
const inside = rel !== '' && !rel.startsWith('..') && !path.isAbsolute(rel);
```

Every function in `node:fs/promises` returns a promise immediately. `readFile` gives a `Buffer`
unless you pass an encoding, error objects carry a stable `code` such as `ENOENT` alongside a
message containing the path, and `mkdtemp` is how you get a directory nobody else is using.

## Common mistakes

**Dropping the promise.** `fsp.readFile(p)` without `await` inside a `try` block cannot be caught:
the block has already ended. The rejection surfaces as `unhandledRejection` and, with no listener,
ends the process. Listing 3 shows all of it.

**`forEach` with an `async` callback.** `forEach` ignores what the callback returns, so the line
after the loop runs with nothing written. Use `for...of` with `await` for sequence, or
`Promise.all` over `map` for concurrency.

**Checking with `access` and then opening.** The answer is about the moment you asked. Open the
file and handle the failure instead of asking permission first.

**Matching on `error.message`.** It contains a machine-specific path and is phrased by the runtime.
Match on `error.code`.

## Check yourself

<details><summary>Why is <code>path.join('uploads', userInput)</code> not enough to keep a file inside <code>uploads</code>?</summary>

`join` will happily collapse `../..` and produce a path above the root. Resolve, then check the
relative path from the root does not start with `..` and is not absolute.

</details>

<details><summary>A <code>try</code>/<code>catch</code> around an <code>fs</code> call catches nothing. What is missing?</summary>

The `await`. Without it the call returns a promise and the block ends before the operation fails,
so there is nothing on the stack to catch.

</details>

<details><summary>Why does <code>readFile</code> sometimes give you a <code>Buffer</code>?</summary>

Because no encoding was passed. With an encoding it decodes to a string; without one it hands
back the bytes.

</details>

## Listings

1. `the-file-system-and-paths-1.js` — `join`, `resolve` and `normalize` on both platform
   implementations, and a containment check.
2. `the-file-system-and-paths-2.js` — a round trip through a temporary directory, printing contents
   rather than paths.
3. `the-file-system-and-paths-3.js` — the missing `await`, in four shapes.
