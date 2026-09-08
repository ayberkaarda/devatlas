## Why this exists

A `for` loop in Python looks the same whether it walks a list already sitting in memory or a stream of values computed one at a time on demand, and that uniformity is deliberate. Underneath, both cases go through the same iterator protocol, which is why a generator function can be dropped into a `for` loop exactly like a list with no special syntax. The same mechanism explains a bug that looks bizarre the first time it happens: removing items from a list while a `for` loop is walking that same list skips entries or repeats them, because the loop and the removal are fighting over the same moving position.

## The idea

Picture a `for` loop as someone reading a book with a bookmark instead of memorizing where they are. Each time through the loop, they ask the bookmark "what's next," read that page, and move the bookmark forward one page. A list is a book that already has every page printed; a generator is a book whose next page gets printed only when the bookmark asks for it, and never printed at all if no one asks. Either way, the reader does not care whether the pages existed in advance, only that a next page is available when requested.

### Where the analogy breaks

A physical book does not change size while someone is reading it, but a list can, and that is exactly where the bookmark idea falls apart: if pages get torn out from earlier in the book while the bookmark is partway through, every later page shifts backward into a spot the bookmark already passed, so the reader silently skips over it. Iterating a generator is safer in this specific way, since nothing is sitting in memory to be edited out from under the loop, but a generator introduces its own limit the book never had: once its bookmark reaches the end, there is no going back to read it again.

## How it works

A generator function uses `yield` instead of `return`, producing one value at a time and pausing between requests rather than computing everything up front.

```python
def squares(limit):
    n = 0
    while n < limit:
        yield n * n
        n += 1
```

Calling `squares(5)` does not run any of that code yet; it hands back an iterator, and each step of the `for` loop resumes the function until the next `yield`. Mutating a list while looping over it is the opposite problem; loop over a copy instead:

```python
for item in list(original):
    if should_remove(item):
        original.remove(item)
```

## Common mistakes

Removing items from a list inside a `for item in items:` loop over that same list causes entries to be silently skipped, with no exception raised at all, because the loop's internal index keeps advancing even as the list shrinks underneath it. A second common mistake is trying to loop over a generator a second time expecting it to start over, which instead produces zero iterations with no error, since a generator that has already run to completion has nothing left to yield.

## Check yourself

**1. Why does `squares(5)` not print anything by itself, without a loop around it?**

<details><summary>Answer</summary>

Calling a generator function only creates a generator object; none of the function's body runs until something iterates over it, such as a `for` loop or a call to `next()`.

</details>

**2. What goes wrong when you call `list.remove()` on items inside a `for` loop over that same list?**

<details><summary>Answer</summary>

The loop tracks position by index, and removing an item shifts every later item one slot earlier. The loop's next index then lands one past where it should, skipping an element.

</details>

## Full listings

The first listing defines a generator that yields squared numbers lazily inside a `while` loop, one value per `yield`. The second listing avoids the mutate-while-iterating trap by building a filtered list separately before assigning it back into the original name.
