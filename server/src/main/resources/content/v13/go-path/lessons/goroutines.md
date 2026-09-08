## Why this exists

Concurrency is normally expensive enough to ration. An operating system thread costs a
megabyte-scale stack and a kernel transition to switch, so programs build pools, queue work
into them and spend real design effort deciding how many they can afford. Go 1.27 makes the
unit cheap instead. A goroutine starts with a small stack that grows as it needs to, and the
runtime multiplexes many of them onto few threads, so the first listing starts ten thousand
without anything remarkable happening. The `go` statement is one keyword: the specification
says the call's function value and parameters are evaluated in the calling goroutine as usual,
but execution does not wait for the call to complete, and any return values are discarded.

## The idea

Starting a goroutine is posting a letter, not making a phone call. You hand the work over and
carry straight on, and nothing comes back down the path you sent it along. If you want an
answer you have to arrange a return address in advance.

### Where the analogy breaks

Three ways, and the first is the one that eats afternoons.

A posted letter arrives eventually. A goroutine may not finish at all. The specification says
that when `main` returns the program exits and *does not wait for other goroutines to
complete*, and the memory model adds that the exit of a goroutine is not guaranteed to be
synchronized before any event in the program. So a result you did not wait for is not late —
it may simply never exist. `sync.WaitGroup` is the return address: in the terminology of the
memory model, a call to `Done` synchronizes before the return of the `Wait` it unblocks, which
is what makes every worker's writes visible after `Wait`.

Postage costs something. A goroutine costs so little that the interesting question stops being
"how many can I afford" and becomes "which ones am I still holding open". A goroutine blocked
forever on a channel nobody reads is a leak, and the third listing observes one as a guarantee
rather than a timing accident: a send on an unbuffered channel cannot complete without a ready
receiver, so the producer is provably still stuck at its send when `main` looks.

And letters do not touch each other. Goroutines share one address space, so two of them
writing the same variable is a data race. The result is not merely late, it is wrong: an
unguarded increment run a hundred thousand times came back as `counter = 98400`, `98513` and
`98390` on three consecutive runs of the same binary.

## How it works

`go f(x)` starts `f` with `x` evaluated now. A `WaitGroup` counts outstanding work, and
`WaitGroup.Go` pairs the increment with the decrement so neither can be forgotten:

```go
var wg sync.WaitGroup
sums := make([]int, len(words))
for i, word := range words {
	wg.Go(func() { sums[i] = checksum(word) })   // one slot each: no lock needed
}
wg.Wait()
```

Results go into storage each goroutine owns — a slot of its own — or through a lock, or through
a channel. Nothing here depends on which goroutine finishes first, which is why the listing
prints the same thing on every run.

The loop variable question changed in Go 1.22: the specification now says each iteration has
its own separate declared variable, so a closure started inside the loop captures that
iteration's variable rather than one shared by all of them. A variable declared *outside* the
loop is still shared, and still needs a lock:

```go
shared := 0
for range 1000 {
	wg.Go(func() {
		mu.Lock()
		shared++          // without the lock this is a data race
		mu.Unlock()
	})
}
```

## Common mistakes

**Passing a `WaitGroup` by value.** `go vet` names both halves of the mistake:

```text
waitgroup-by-value.go:8:14: work passes lock by value: sync.WaitGroup contains sync.noCopy
waitgroup-by-value.go:17:11: call of work copies lock value: sync.WaitGroup contains sync.noCopy
```

**Receiving more values than anyone will send.** The runtime notices when nothing can proceed:

```text
1
fatal error: all goroutines are asleep - deadlock!
```

**Sharing a counter without a lock.** No compile error, no panic, just an answer that is close
enough to look right in a small test:

```text
counter = 98400 expected 100000
counter = 98513 expected 100000
counter = 98390 expected 100000
```

**Sleeping instead of waiting.** A `time.Sleep` chosen to be "long enough" is a guess that is
wrong on a loaded machine and slow on an idle one. Waiting is not an approximation.

## Check yourself

<details><summary>Why does a fan-out that writes to <code>results[i]</code> need no lock?</summary>

Because no two goroutines touch the same element and nothing resizes the slice. The header is
read-only for the duration and each write goes to storage only one goroutine can reach. `Wait`
then supplies the ordering that makes those writes visible to the reader.

</details>

<details><summary>Since Go 1.22, is the classic loop-variable capture bug still there?</summary>

Not for variables the loop declares: each iteration has its own. A variable declared before the
loop is still one variable, so a goroutine that closes over it shares it with every other
iteration.

</details>

<details><summary>What makes a blocked goroutine a leak rather than a pause?</summary>

That nothing can ever unblock it. A send on a channel with no receiver, or a receive on a
channel nobody will send to and nobody will close, waits for an event that is not going to
happen; the goroutine and everything it holds stay alive for the life of the process.

</details>

## Listings

1. `goroutines-1.go` — fan-out, one slot per worker, and ten thousand goroutines.
2. `goroutines-2.go` — what a goroutine captures, and `sync.Once`.
3. `goroutines-3.go` — a leak observed as a guarantee, and the cancellation that fixes it.
