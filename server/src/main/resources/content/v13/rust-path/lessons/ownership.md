## Why this exists

A program that allocates has to give the memory back, and before Rust there were two
answers. A garbage collector decides for you, at a moment you do not choose, and charges a
runtime for the service. Manual `free` is exact, and hands you the double free, the leak and
the use-after-free. Rust 1.98 takes a third answer: every value has exactly one owner, that
owner is a variable or a field, and when the owner goes out of scope the value is destroyed
right there. Nothing is scanned, nothing is counted, and the point where cleanup happens is
decided while the program is compiled rather than while it runs.

## The idea

Ownership is the on-call pager. Exactly one person holds it. Handing it to the next person
is a transfer and not a copy: afterwards you cannot page yourself, because you do not have it
any more. Whoever is holding it when the shift ends is the one who logs it out, and nobody
has to remember to do that on your behalf.

### Where the analogy breaks

In three ways, and each one is something readers get wrong.

A pager can come back to you, and everyone in the room can see that it did. Move checking in
Rust 1.98 is static: it reasons about every path through the source, not about what happened
on this particular run. A value moved inside a loop is rejected on the strength of the second
iteration even though the first one was fine, and the compiler says exactly that — `value
moved here, in previous iteration of loop`.

Some values do not hand over at all. `i32`, `bool`, `char` and every type implementing the
`Copy` marker trait are duplicated by assignment, and both names stay usable afterwards.
There is no pager you can photocopy.

And the handover is not negotiated. A value is dropped at the end of its scope, and variables
are dropped in reverse order of declaration — an ordering the Rust 1.98 reference specifies
rather than leaving to the implementation. There is also no runtime record of who owns what.
The compiler writes the call to `drop` into the program, and at run time there is nothing
left to ask.

## How it works

A binding owns its value. Passing that value to a function, returning it, or assigning it to
another binding moves ownership, and after a move the old name is dead. A function parameter
is an owner like any other, so a call can consume a value outright; returning it hands
ownership back to the caller. Cleanup is a trait: implementing `Drop` gives a type a
destructor the compiler calls at the end of the owner's scope.

```rust
struct Tracked { name: &'static str }

impl Drop for Tracked {
    fn drop(&mut self) { println!("drop {}", self.name); }
}

fn consume(item: Tracked) { }   // `item` is dropped when this returns

let value = Tracked { name: "package" };
let stored = value;             // moved: `value` cannot be named again
consume(stored);                // moved again, and dropped inside the call
```

Two things opt out of moving. `Copy` types are duplicated instead, which is why arithmetic on
integers does not litter code with clones. And `clone()` produces a second, independent value
on request — an explicit cost written at the place where it is paid, rather than a hidden
one. Moving a value out of a field that is still borrowed needs something to leave behind,
which is what `Option::take` is for: it hands you the inside and stores `None` in its place.

## Common mistakes

**Using a value after passing it on.** The first error most people meet, and the message
names both the type and the reason:

```text
error[E0382]: borrow of moved value: `id`
6 |     let id = String::from("lesson-1");
  |         -- move occurs because `id` has type `String`, which does not implement the `Copy` trait
7 |     let n = store(id);
  |                   -- value moved here
8 |     println!("{n} {id}");
  |                    ^^ value borrowed here after move
```

**Moving the same value on every pass of a loop.** One move is fine; the second is not, and
the compiler reports the iteration rather than the line:

```text
error[E0382]: use of moved value: `payload`
8 |         println!("{}", consume(payload));
  |                                ^^^^^^^ value moved here, in previous iteration of loop
```

**Taking a value out of a collection by indexing.** Indexing gives you a place, not ownership
of what is sitting in it:

```text
error[E0507]: cannot move out of index of `Vec<String>`
3 |     let first: String = names[0];
  |                         ^^^^^^^^ move occurs because value has type `String`, which does not implement the `Copy` trait
```

All three offer the same two repairs, borrowing or cloning. Borrowing is the right one far
more often, and it is the next lesson.

## Check yourself

<details><summary>Why can an <code>i64</code> be used after being passed to a function, when a <code>String</code> cannot?</summary>

`i64` implements `Copy`, so the call duplicated the bits and left the original binding
intact. `String` owns a heap buffer, and duplicating it would mean either two owners of one
buffer or a silent allocation. Rust does neither: it moves.

</details>

<details><summary>In what order are two variables declared in the same block destroyed?</summary>

Reverse order of declaration — the one declared second is dropped first. The Rust 1.98
reference specifies this, so a program is entitled to rely on it.

</details>

<details><summary>A function takes a value and returns it unchanged. Was anything dropped?</summary>

No. Ownership moved in and then straight back out. A value is dropped only when the scope of
whoever owns it at that moment ends while it is still held.

</details>

## Listings

1. `ownership-1.rs` — scopes, drop order, and a value consumed by a function.
2. `ownership-2.rs` — move, `Copy` and `clone` side by side.
3. `ownership-3.rs` — ownership inside a container, and moving a value back out.
