<?php

declare(strict_types=1);

set_error_handler(static function (int $severity, string $message): bool {
    $label = match ($severity) {
        E_DEPRECATED, E_USER_DEPRECATED => 'Deprecated',
        E_WARNING, E_USER_WARNING => 'Warning',
        default => 'Diagnostic',
    };
    echo $label, ': ', $message, "\n";

    return true;
});

// Only int and string are array keys. Everything else is converted first.
$keys = [];
$keys['1'] = 'string one';
$keys[1.7] = 'float';
$keys[true] = 'bool true';
$keys[null] = 'null';
$keys['01'] = 'leading zero is not an int-string';

foreach ($keys as $key => $value) {
    printf("%-6s (%s) => %s\n", var_export($key, true), get_debug_type($key), $value);
}

// Removing an entry leaves a gap. It does not renumber, and it does not lower
// the next free integer key.
$rows = ['a', 'b', 'c'];
unset($rows[1]);
var_dump(array_is_list($rows));
echo implode(',', array_keys($rows)), "\n";
$rows[] = 'd';
echo implode(',', array_keys($rows)), "\n";

// json_encode looks at the same property: a list becomes an array, anything
// else becomes an object. This is where the gap becomes someone else's bug.
$gapped = ['a', 'b', 'c'];
unset($gapped[1]);
echo json_encode($gapped), "\n";
echo json_encode(array_values($gapped)), "\n";

// array_filter preserves keys, so filtering a list usually stops producing one.
$scores = [10, 3, 8, 1];
$high = array_filter($scores, static fn (int $s): bool => $s > 5);
var_dump(array_is_list($high));
echo json_encode($high), "\n";
echo json_encode(array_values($high)), "\n";
