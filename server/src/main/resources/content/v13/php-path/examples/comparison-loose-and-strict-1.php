<?php

declare(strict_types=1);

// strict_types governs parameter and return coercion. It does not change ==,
// which is a language operator and keeps its own rules in every file.

function show(mixed $value): string
{
    return is_array($value) ? '[]' : var_export($value, true);
}

/** @param array{0: mixed, 1: mixed} $pair */
function row(array $pair): string
{
    [$left, $right] = $pair;

    return sprintf(
        "%-10s %-10s  ==:%-5s  ===:%-5s",
        show($left),
        show($right),
        var_export($left == $right, true),
        var_export($left === $right, true),
    );
}

$pairs = [
    [0, 'foo'],       // true in PHP 7, false since PHP 8.0
    [0, ''],
    [0, '0'],
    ['1', '01'],
    ['10', '1e1'],
    [100, '1e2'],
    ['abc', 0],
    [null, false],
    [null, 0],
    [[], false],
    ['0', false],
    [1, true],
];

foreach ($pairs as $pair) {
    echo row($pair), "\n";
}

// The PHP 8.0 rule in one sentence: a number compared with a non-numeric string
// makes the number a string first, instead of making the string a number.
var_dump(0 == 'foo');
var_dump('foo' == '0');
var_dump(0 == '0');

// Numeric strings still compare numerically, and that is a separate surprise.
var_dump('1' == '01', '1' === '01');
