<?php

declare(strict_types=1);

// An array is a value. Assigning one copies it (lazily, but observably). An
// object is a handle. The difference decides who sees your change.

$original = ['a', 'b'];
$copy = $original;
$copy[] = 'c';
echo json_encode($original), ' ', json_encode($copy), "\n";

final class Basket
{
    /** @var list<string> */
    public array $items = [];
}

$one = new Basket();
$two = $one;              // same object
$two->items[] = 'apple';
echo json_encode($one->items), "\n";

$three = clone $one;      // shallow copy; the array inside is a value, so it copies
$three->items[] = 'pear';
echo json_encode($one->items), ' ', json_encode($three->items), "\n";

// Passing an array to a function passes a value.
function drain(array $items): int
{
    $items = [];

    return count($items);
}
$stock = ['x', 'y'];
var_dump(drain($stock), count($stock));

// foreach by reference leaves $letter bound to the last element. Reusing the
// same variable in a second loop then writes through that binding.
$letters = ['a', 'b', 'c'];
foreach ($letters as &$letter) {
    $letter = strtoupper($letter);
}
foreach ($letters as $letter) {
    // no body: the assignment foreach performs is the whole bug
}
echo json_encode($letters), "\n";

// unset() after the by-reference loop breaks the binding.
$safe = ['a', 'b', 'c'];
foreach ($safe as &$item) {
    $item = strtoupper($item);
}
unset($item);
foreach ($safe as $item) {
}
echo json_encode($safe), "\n";
