## Why this exists

Two goroutines that share a variable need a lock, and a lock is a rule that lives outside the
code it governs: nothing in the type of an `int` says which mutex guards it, so the rule is
kept by convention and broken by the next contributor. A channel moves the value instead of
guarding it. Handing a value to a channel is both the transfer and the synchronisation, and
the Go 1.27 memory model states the guarantee: a send on a channel is synchronized before the
completion of the corresponding receive from that channel. Everything the sender wrote before
the send is visible to whoever receives it. The rule is in the mechanism rather than in a
comment above it.

## The idea

A channel is a serving hatch between two kitchens. An unbuffered channel is a hatch with no
shelf: the cook stands holding the plate until a waiter's hands are actually there, and the
handover is a single moment both sides took part in. A buffered channel is a hatch with a shelf
of a fixed number of plates.

### Where the analogy breaks

Three ways.

A hatch is passive furniture; a channel also publishes memory. The handover is not only a
transfer of one value but an ordering point, which is what makes it a replacement for a lock
rather than an addition to one. A hatch has nothing to say about what the cook wrote on the
whiteboard before putting the plate down.

A hatch does not announce that it has shut. A channel does, and only the sending side may do
it: after `close`, every receive returns immediately with the element type's zero value and
`ok == false`, and a `range` over the channel ends. That broadcast is why closing is used as a
cancellation signal. It is also sharp — sending on a closed channel panics, and so does closing
one twice.

And a chef does not choose between two waiters by coin toss. `select` does: the specification
says that if more than one communication can proceed, a single one is chosen by a *uniform
pseudo-random selection*. There is no queue, no priority and no fairness order a program may
depend on, which is exactly why the third listing prints sums and sorted sets rather than the
sequence one run produced.

## How it works

An unbuffered channel is a rendezvous — the specification says communication succeeds only when
both a sender and a receiver are ready. A single producer's sends are received in the order
they were made, so a `range` over the channel is ordered even though the goroutines are not:

```go
squares := make(chan int)
go func() {
	for n := 1; n <= 5; n++ {
		squares <- n * n
	}
	close(squares)          // the producer closes; the consumer never does
}()
for v := range squares {    // ends when the channel is closed and drained
	use(v)
}
```

A capacity decouples the two sides up to that many elements. Directional types put the rule in
the signature: a `chan<- int` parameter can only be sent to, and a `<-chan int` can only be
received from and cannot be closed.

`select` waits on several operations at once. A `default` case makes it non-blocking, and
setting a channel variable to `nil` removes its case permanently, because a nil channel is
never ready:

```go
for evens != nil || odds != nil {
	select {
	case v, ok := <-evens:
		if !ok { evens = nil; continue }   // drop this case and keep merging
		total += v
	case v, ok := <-odds:
		if !ok { odds = nil; continue }
		total += v
	}
}
```

## Common mistakes

**Sending on a channel that has been closed.** The panic is immediate and unambiguous:

```text
panic: send on closed channel
```

**Sending to a receive-only parameter**, which the compiler catches before anything runs:

```text
.\send-to-receive-only.go:6:2: invalid operation: cannot send to receive-only channel <-chan int in (variable of type <-chan int)
```

**Closing a channel from the receiving side.** The direction in the type makes it impossible:

```text
.\close-receive-only.go:4:8: invalid operation: cannot close receive-only channel in (variable of type <-chan int)
```

**Ranging over a channel nobody closes.** The values arrive, and then the program stops for
good:

```text
1
2
fatal error: all goroutines are asleep - deadlock!
```

**Recording which case of a `select` won.** Not a diagnostic but a broken test: the choice
among ready cases is random by specification, so an assertion about it fails intermittently.

## Check yourself

<details><summary>Which side closes a channel, and why does it matter?</summary>

The sending side. A receiver cannot know whether another sender is still running, and closing
a channel a sender then writes to panics. Directional parameter types make the rule mechanical:
a `<-chan T` cannot be closed at all.

</details>

<details><summary>What does setting a channel variable to <code>nil</code> do inside a <code>select</code>?</summary>

A nil channel is never ready, so that case can never be chosen again. It is the idiomatic way
to retire one input of a merge while the others keep going, instead of tracking a separate
boolean per input.

</details>

<details><summary>Two cases of a <code>select</code> are both ready. Which runs?</summary>

One chosen by uniform pseudo-random selection. Nothing about the order in which the cases are
written affects it, so a program must not depend on which one wins.

</details>

## Listings

1. `channels-and-select-1.go` — the unbuffered handshake, closing, and ordered receives.
2. `channels-and-select-2.go` — capacity, directional types, and a semaphore.
3. `channels-and-select-3.go` — fan-out and fan-in, merging with `nil`, and cancellation.
