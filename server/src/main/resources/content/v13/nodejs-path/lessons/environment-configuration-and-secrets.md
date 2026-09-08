## Why this exists

The same build has to run on a laptop, in continuous integration and in production, and the three
differ in a database URL, a port and a set of credentials. Putting those in the environment is the
standard answer, and `process.env` in Node.js 22 makes it a one-liner. It also makes four quiet
mistakes a one-liner: everything is a string, a missing variable is `undefined` rather than an error,
a secret read into a config object is one `console.log` away from a log aggregator, and a child
process inherits the whole environment unless you say otherwise. None of these fail loudly. They
fail at the worst moment or, in the case of the secret, they do not fail at all.

## The idea

The environment is a noticeboard in the corridor outside the room, not a safe inside it. Anyone
who walks in has read it, and anyone you send out of the room takes a copy of the whole board
with them.

### Where the analogy breaks

A corridor noticeboard is readable by passers-by. Environment variables are not: they belong to
one process, and on a normal system an unrelated user cannot read them. The leak path is not the
corridor — it is descendants, crash dumps, error reports and anything that serialises the process
state.

A noticeboard also holds notes of any kind. `process.env` holds strings and only strings, and it
converts silently on the way in: assign the number `8080` and read back `'8080'`; assign `false` and
read back `'false'`, which is a non-empty string and therefore truthy.

And a noticeboard is a shared surface. `process.env` is per-process and a value read out of it is a
copy, so changing the variable later does not change what you already read, and changing your
copy does not change the variable.

## How it works

Read the environment once, at start-up, and convert and validate everything there. A configuration
error should stop the process immediately with a message naming every problem, not surface as a
`NaN` port three minutes later.

```js
const port = Number(env.PORT ?? '3000');
if (!Number.isInteger(port) || port < 1 || port > 65535) problems.push('PORT must be a port number');
```

Note which default operator you want. `??` only replaces `undefined` and `null`, so an empty
variable stays empty; `||` also replaces the empty string. For an environment variable, an empty
value almost always means "not set", and `||` is usually right.

Secrets get a wrapper rather than a bare string, so the redaction is a property of the value and not
a rule people have to remember at every log statement.

```js
class Secret {
  #value;
  toString() { return '[redacted]'; }
  toJSON() { return '[redacted]'; }
  [util.inspect.custom]() { return '[redacted]'; }
  reveal() { return this.#value; }
}
```

What you pass on is a separate decision. `child_process` inherits `process.env` by default, so every
tool you shell out to sees every credential you hold. Passing an explicit object is the whole fix.
For development, Node.js 22 can read a `key=value` file into the environment with `--env-file`,
which keeps a secret out of shell history and out of the process list — a convenience, not a
secret store.

## Common mistakes

**Comparing an environment variable to a boolean.** `process.env.DEBUG === true` is never true, and
`if (process.env.DEBUG)` is true for the string `'false'`. Parse it.

**Using `??` where you meant `||`.** An exported-but-empty variable keeps its empty value and the
default never applies.

**Logging the config object.** Without a redacting wrapper, `JSON.stringify(config)` puts the token
in the log. Listing 2 prints the same object with the token replaced in a template string, in
`JSON.stringify` and in `util.inspect`.

**Spawning a tool with the inherited environment.** Listing 3 shows the child reading a token it
had no business seeing, and the allow-list that stops it.

## Check yourself

<details><summary><code>process.env.RETRIES = 3</code>, then <code>process.env.RETRIES + 1</code>. What do you get?</summary>

The string `'31'`. The assignment coerced 3 to `'3'`, and `+` on a string concatenates.

</details>

<details><summary>Why wrap a secret in an object rather than keeping the string?</summary>

Because redaction then travels with the value. A bare string is redacted only where somebody
remembered to redact it, and log statements are added by people who did not read this lesson.

</details>

<details><summary>Which variables should a child process receive?</summary>

The ones it needs, named explicitly. Inheriting the parent's environment hands every credential in
the process to every tool it starts.

</details>

## Listings

1. `environment-configuration-and-secrets-1.js` — the coercions, the defaults and the parsing that
   has to be written out.
2. `environment-configuration-and-secrets-2.js` — configuration validated at start-up, with a
   secret that redacts itself.
3. `environment-configuration-and-secrets-3.js` — what a child process inherits, and the allow-list.
