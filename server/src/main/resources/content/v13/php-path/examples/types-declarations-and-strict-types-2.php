<?php

// This is the one listing in the track with no declare(strict_types=1), because
// coercive mode is what it exists to show. Every other listing declares it.

set_error_handler(static function (int $severity, string $message): bool {
    // Printing the label and the text only keeps the file name and line out of
    // the recorded output.
    $label = match ($severity) {
        E_DEPRECATED, E_USER_DEPRECATED => 'Deprecated',
        E_WARNING, E_USER_WARNING => 'Warning',
        E_NOTICE, E_USER_NOTICE => 'Notice',
        default => 'Diagnostic',
    };
    echo $label, ': ', $message, "\n";

    return true;
});

function seats(int $count): int
{
    return $count;
}

$inputs = [' 8', '8 ', '8.0', '8.5', '008', '1e2', '0x1A', 'eight', true, false, 8.5];

foreach ($inputs as $input) {
    $shown = var_export($input, true);
    try {
        printf("%-8s -> %d\n", $shown, seats($input));
    } catch (TypeError $e) {
        printf("%-8s -> TypeError\n", $shown);
    }
}

// Arithmetic has its own rules, separate from parameter coercion.
var_dump('8 apples' + 1);

try {
    var_dump('apples' + 1);
} catch (TypeError $e) {
    echo 'TypeError: ', $e->getMessage(), "\n";
}
