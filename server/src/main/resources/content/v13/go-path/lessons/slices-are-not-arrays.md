## Why this exists

Almost every Go program uses slices and almost none use arrays directly, yet the two are
constantly confused because `[]int` and `[4]int` look like variations on one idea. They are
not. An array in Go 1.27 is a value whose length is part of its type: `[3]int` and `[4]int`
are different types, and assigning an array copies every element. A slice is a three-field
header — a pointer to a backing array, a length and a capacity — and assigning a slice copies
only those three fields. Two slices can therefore describe the same storage, and a write
through one is visible through the other. Every slice bug worth the name comes from not
knowing which of those two things is in your hand.

## The idea

A slice is a bookmark into a book, not the book. It records where you started, how many pages
you are claiming, and how many pages remain to the end of the volume. Copying the bookmark
does not copy the book, so two readers with bookmarks into the same book see each other's
pencil marks.

### Where the analogy breaks

Three ways, and each is a real defect people ship.

A bookmark cannot lengthen the book. `append` can. When the claimed length is still inside the
capacity, `append` writes into the existing pages, so an append through one bookmark can
overwrite a page another bookmark was already reading. When the capacity runs out, `append`
allocates a new backing array and copies — and after that the two bookmarks are in different
books. Nothing at the call site announces which of the two just happened, which is why the
specification's guarantee is only that the result contains the elements, not where they live.

A bookmark's "pages remaining" is the rest of the whole volume. That is exactly what a slice's
capacity is: the specification defines `cap(a[low:high])` as `cap(a) - low`, so a short slice
cut from the front of a long one still has the whole tail in reach. The three-index form
`a[low:high:max]` is how you say "and no further", forcing the next `append` to allocate.

And a book has one length, whereas an array's length lives in its type. `[3]int` cannot be
passed where `[4]int` is wanted, and no bookmark analogy has anything to say about that.

## How it works

An array is copied on assignment, on a function call and on a return. A slice header is copied
too — which is the trap, because copying the header duplicates the pointer:

```go
array := [4]int{10, 20, 30, 40}
slice := []int{10, 20, 30, 40}

arrayCopy := array
arrayCopy[0] = 1     // array is unchanged
sliceCopy := slice
sliceCopy[0] = 1     // slice[0] is now 1
```

Because the header is a value, a function that appends to its parameter changes its own header
and the caller sees nothing. That is why `append` is written as an assignment: `s = append(s,
v)`. The same reasoning explains the aliasing bug and its repair:

```go
base := []int{0, 1, 2, 3, 4, 5, 6, 7}
head := base[0:3]      // len 3, cap 8 — the tail is still in reach
head = append(head, 100)
// base is now [0 1 2 100 4 5 6 7]

guarded := base[0:3:3] // len 3, cap 3
guarded = append(guarded, 100)  // must allocate; base is untouched
```

`copy(dst, src)` is the explicit way to get storage of your own; it moves the smaller of the
two lengths and returns that count, and a short destination is not an error.

## Common mistakes

**Discarding the result of `append`.** The compiler refuses, which is one of the kinder
things it does:

```text
.\append-discarded.go:7:2: append(queue, "b") (value of type []string) is not used
```

**Comparing two slices with `==`.**

```text
.\slice-compare.go:8:14: invalid operation: a == b (slice can only be compared to nil)
```

**Passing an array of the wrong length.** The length is part of the type, so this is a type
error rather than a bounds error:

```text
.\array-length.go:8:7: cannot use [3]int{…} (value of type [3]int) as [4]int value in argument to show
```

**Indexing past the length, when the capacity made it look reachable.**

```text
panic: runtime error: index out of range [2] with length 2
```

**Editing the loop variable in a `range` over a slice of structs.** No diagnostic at all — the
program runs and the edit is lost, because `v` is a copy. The third listing prints both
outcomes side by side: `[{write false} {review false} {ship false}]` after ranging by value,
and `[{write true} {review true} {ship true}]` after ranging by index.

## Check yourself

<details><summary>Why is <code>s = append(s, v)</code> written as an assignment?</summary>

Because `append` may allocate a new backing array, and because the slice header is a value.
The function receives a copy of the header and returns the header that describes the result;
without the assignment that result is discarded.

</details>

<details><summary>What does the third index in <code>a[1:3:3]</code> do?</summary>

It sets the capacity of the result to `max - low`. Here the capacity equals the length, so the
next `append` cannot write into the original backing array and must allocate.

</details>

<details><summary>Does <code>copy</code> fail when the destination is shorter than the source?</summary>

No. It copies the smaller of the two lengths and returns the number of elements moved, so a
destination of length three takes the first three and stops.

</details>

## Listings

1. `slices-are-not-arrays-1.go` — an array is a value, a slice is a header.
2. `slices-are-not-arrays-2.go` — sharing, `append`, and the three-index form.
3. `slices-are-not-arrays-3.go` — `range` copies the element, and what to do instead.
