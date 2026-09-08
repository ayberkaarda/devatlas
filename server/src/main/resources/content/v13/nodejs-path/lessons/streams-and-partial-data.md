## Why this exists

A file you can read with `fs.readFile` is a file that fits in memory. A 4 GB export, an upload from
a client, a response from an API that is still arriving — none of those do, and none of them are
finished when you have to start work. Streams in Node.js 22 are the interface for data that arrives
in pieces, and they carry a second thing that is easy to miss: a way for a slow consumer to tell a
fast producer to stop. Without that, "process it as it arrives" quietly becomes "buffer all of it in
memory anyway", and the program that was supposed to be frugal is the one that gets killed.

## The idea

A stream is a conveyor belt at baggage reclaim. Items arrive in the order they were sent, you can
only reach what is in front of you, and if you take things off slower than they are put on, they
pile up at your end.

### Where the analogy breaks

A belt cannot be asked to slow down. A Node stream can, and that is the whole of backpressure:
`write()` returns `false` when the destination's buffer has reached its high water mark, and the
`'drain'` event says when to resume. Ignoring that return value does not lose data — it buffers it,
in your process, indefinitely.

The belt also delivers whole bags. A stream delivers arbitrary byte ranges. A chunk boundary has
no relationship to a line, a record or even a UTF-8 character, so a reader that treats each chunk as
a unit will split a name in half. Chunk sizes are decided by the source, and they change when the
file, the disk or the network changes.

## How it works

A readable stream is async-iterable, so a `for await` loop is usually the clearest consumer. What it
gives you is chunks, and turning chunks into records means carrying the incomplete tail forward.

```js
let carry = '';
for await (const chunk of readable) {
  const parts = (carry + chunk).split('\n');
  carry = parts.pop();          // may be half a line
  for (const line of parts) handle(line);
}
if (carry !== '') handle(carry);
```

A `Transform` is a stream that is both a destination and a source, and `pipeline` from
`node:stream/promises` connects a chain of them. `pipeline` is not a convenience over `.pipe()`: it
propagates errors, destroys every stream in the chain when one fails, and gives you a promise that
rejects with the error that ended the run.

```js
await pipeline(source, splitLines(), validate(), sink);
```

Writing has a return value, and it means something. `write()` answers `true` while the internal
buffer is below the configured `highWaterMark` and `false` once it is not; after a `false`, the
producer should wait for `'drain'`.

```js
if (!sink.write(record)) await once(sink, 'drain');
```

## Common mistakes

**Splitting each chunk on its own.** Listing 1 does exactly this and produces `1,al` and `ice` as two
separate rows from one name. It looks correct on small inputs, because small inputs arrive in one
chunk.

**Concatenating a stream into a string with `+=` and calling it streaming.** It is not; you have
written a slower `readFile` that also holds the whole result in memory.

**Ignoring the result of `write()`.** The data is still accepted, so nothing fails and nothing warns.
The queue grows until the process does. Listing 3 shows five writes, four refusals and all five
chunks still resident.

**Chaining with `.pipe()` and handling errors on one end.** An error in the middle of a `.pipe()`
chain leaves the other streams open. Use `pipeline`, which destroys them.

## Check yourself

<details><summary>Why does a chunk boundary have nothing to do with a line boundary?</summary>

Chunks are however many bytes the source had available. The producer knows nothing about the
structure of your data, so a boundary can land anywhere, including inside a record.

</details>

<details><summary><code>write()</code> returned <code>false</code> and you wrote anyway. What happens?</summary>

The chunk is buffered rather than dropped. Nothing fails; memory grows for as long as the producer
stays ahead of the consumer.

</details>

<details><summary>Why prefer <code>pipeline</code> over a chain of <code>.pipe()</code> calls?</summary>

`pipeline` propagates the failure and destroys every stream in the chain, so a failure in the middle
does not leave file handles and sockets open.

</details>

## Listings

1. `streams-and-partial-data-1.js` — chunk boundaries against record boundaries, and the reader
   that carries the remainder.
2. `streams-and-partial-data-2.js` — a `pipeline` of transforms, and what happens to the chain
   when one link fails.
3. `streams-and-partial-data-3.js` — backpressure: the return value of `write()` and the `'drain'`
   event.
