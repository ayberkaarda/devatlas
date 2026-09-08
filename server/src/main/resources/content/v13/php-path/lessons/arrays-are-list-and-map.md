## Why this exists

PHP has one built-in collection and it plays two roles. Written `['a', 'b', 'c']` it looks like a
list; written `['name' => 'ana']` it looks like a map; and the language makes no distinction
between them, because there is only one type underneath. That single structure is convenient
until something outside PHP has to be told which of the two it was — a JSON response, a database
driver, a template — and then the difference is suddenly load-bearing.

The manual describes a PHP array as an ordered map, and every property in this lesson follows from
that phrase. PHP 8.1 added `array_is_list()`, which is the only reliable way to ask which role a
particular array is playing, and PHP 8.2 keeps that behaviour unchanged.

## The idea

An array is a filing cabinet in which every folder carries a label and the folders stay in the
order they were filed. A "list" is not a different cabinet; it is the case where the labels happen
to read 0, 1, 2 … with no gaps and in that order.

### Where the analogy breaks

A cabinet takes any label you write. A PHP array takes only `int` and `string` keys, and it
converts anything else before filing: `true` becomes `1`, `null` becomes `''`, a float is
truncated with a deprecation notice, and the string `'1'` becomes the integer `1` while `'01'`
stays a string. Two labels you thought were different can turn out to be the same slot.

The cabinet analogy also suggests that pulling a folder out closes the gap. It does not. `unset()`
removes the entry and leaves the remaining keys exactly as they were, and it does not lower the
next integer key the array will hand out — after removing key `1` from a three-element list, the
next appended element gets key `3`.

The last difference is the one that changes how you write code: the cabinet is a *value*. Assigning
an array to another variable, or passing it to a function, gives the other side its own copy. An
object, by contrast, is a handle to one thing.

## How it works

Order is a specified property of a PHP array, not an accident of the implementation, so printing
keys in iteration order is reproducible. `array_is_list()` reports the other property:

```php
$queue = [];
$queue[] = 'first';
$queue[] = 'second';
array_is_list($queue);          // true

$byName = ['zoe' => 3, 'ana' => 1];
array_is_list($byName);         // false
implode(',', array_keys($byName));   // "zoe,ana" — insertion order, always
```

Key conversion happens on the way in, and five different-looking keys collapse to three:

```
Deprecated: Implicit conversion from float 1.7 to int loses precision
1      (int) => bool true
''     (string) => null
'01'   (string) => leading zero is not an int-string
```

This is where the two roles start to matter to something else. `json_encode()` asks the same
question `array_is_list()` asks, and answers with a JSON array or a JSON object:

```
{"0":"a","2":"c"}
["a","c"]
```

The first line is what a list produces after one `unset()`. `array_filter()` creates the same
situation, because it preserves keys, so filtering a list almost never leaves a list.
`array_values()` is the repair.

## Common mistakes

**Assuming `unset()` renumbers.** It does not, and the array stops being a list from that moment.
A response that was a JSON array yesterday becomes a JSON object today, and the client that
consumed it breaks somewhere else entirely.

**Filtering and then encoding.** `array_filter($rows, ...)` followed by `json_encode()` is the
same bug with a friendlier face. Wrap it in `array_values()` when the result is going out as a
list.

**Reusing the loop variable after `foreach` by reference.** The variable stays bound to the last
element, so the next loop over the same array writes through it:

```
["A","B","B"]
```

The third element was overwritten by the second. `unset($item)` after the by-reference loop ends
the binding, and the output becomes `["A","B","C"]`.

**Expecting a copy of an object's array to be independent of the object.** Cloning is shallow, but
the array *inside* the object is a value and does copy; two variables pointing at the same object
do not.

## Check yourself

<details><summary><code>$a = ['x','y','z']; unset($a[1]); $a[] = 'w';</code> — what are the keys?</summary>

`0`, `2`, `3`. `unset()` left the gap, and the next integer key continued from the highest one ever
used rather than from the current contents.

</details>

<details><summary>Why does printing the keys of a PHP array count as evidence, when printing a hash map's order would not?</summary>

Insertion order is part of what a PHP array is. A hash map in most other languages specifies no
order, so its iteration order is an artefact of one run.

</details>

<details><summary>A function takes an array, empties it, and returns. What does the caller see?</summary>

Its own array, unchanged. Arrays are passed by value. Only `&$array` in the signature, or an object
wrapping the array, would let the function affect the caller.

</details>

## Listings

1. `arrays-are-list-and-map-1.php` — the two roles, `array_is_list()`, and iteration order.
2. `arrays-are-list-and-map-2.php` — key conversion, the gap `unset()` leaves, and what
   `json_encode()` does with each.
3. `arrays-are-list-and-map-3.php` — value semantics, object handles, and the `foreach` reference
   that outlives its loop.
