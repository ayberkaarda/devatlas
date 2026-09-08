<?php

declare(strict_types=1);

// One data structure: an ordered map. A "list" is the special case where the
// keys are 0..n-1 in that order, and array_is_list() is the test for it.

$queue = [];
$queue[] = 'first';
$queue[] = 'second';
$queue[] = 'third';

var_dump(array_is_list($queue));
echo implode(',', $queue), "\n";

$byName = [];
$byName['zoe'] = 3;
$byName['ana'] = 1;
$byName['mo'] = 2;

var_dump(array_is_list($byName));

// Insertion order is a specified property of a PHP array, so printing the keys
// in iteration order is reproducible rather than accidental.
echo implode(',', array_keys($byName)), "\n";

ksort($byName);
echo implode(',', array_keys($byName)), "\n";

// Mixing the two is legal and the result is still one ordered map.
$mixed = ['a', 'b', 'label' => 'x', 'c'];
foreach ($mixed as $key => $value) {
    printf("%s => %s\n", var_export($key, true), $value);
}
var_dump(array_is_list($mixed));

// count() counts entries, not the highest index.
var_dump(count($mixed), array_key_last($mixed));
