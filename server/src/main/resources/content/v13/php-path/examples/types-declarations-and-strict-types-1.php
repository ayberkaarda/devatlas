<?php

declare(strict_types=1);

// Strict mode. Every call written in this file is checked without conversion,
// with one documented widening: int is accepted where float is declared.

function describe(Throwable $e): string
{
    // The message can name this file and the line it was called from. That is
    // machine specific, so it is trimmed before the message is printed.
    $message = preg_replace('~,? (?:called )?in .+? on line \d+~', '', $e->getMessage());

    return $e::class . ': ' . $message;
}

function halve(int $count): float
{
    return $count / 2;
}

function label(int|string $id): string
{
    return is_int($id) ? "int#{$id}" : "str#{$id}";
}

function firstWord(?string $text): string
{
    return $text === null ? '(none)' : strtok($text, ' ');
}

function area(float $side): float
{
    return $side * $side;
}

var_dump(halve(8));
var_dump(label(7), label('7'));
var_dump(firstWord(null), firstWord('hello there'));

// int -> float is the one implicit widening strict mode still performs.
var_dump(area(3));

try {
    halve('8');
} catch (TypeError $e) {
    echo describe($e), "\n";
}

try {
    halve(8.0);
} catch (TypeError $e) {
    echo describe($e), "\n";
}

try {
    label(null);
} catch (TypeError $e) {
    echo describe($e), "\n";
}
