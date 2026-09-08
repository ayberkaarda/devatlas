<?php

declare(strict_types=1);

// The default display of a diagnostic names this file and its line, which is
// machine specific. Turning the display off and printing the parts we want
// keeps the recorded output the same everywhere.
ini_set('display_errors', '0');
ini_set('log_errors', '0');

// A shutdown function is the only hook that survives a fatal error.
register_shutdown_function(static function (): void {
    $last = error_get_last();
    if ($last === null) {
        echo "shutdown: nothing pending\n";

        return;
    }
    $fatal = ($last['type'] & (E_ERROR | E_USER_ERROR | E_PARSE | E_COMPILE_ERROR)) !== 0;
    printf("shutdown: fatal=%s message=%s\n", var_export($fatal, true), $last['message']);
});

$row = ['id' => 1];

// A warning is not a Throwable. try/catch does not see it, and the expression
// still produces a value.
try {
    $missing = $row['name'] ?? '(absent)';
    echo "null coalescing avoided the diagnostic entirely: {$missing}\n";
} catch (Throwable $e) {
    echo "unreachable\n";
}

set_error_handler(static function (int $severity, string $message): bool {
    $label = match ($severity) {
        E_DEPRECATED, E_USER_DEPRECATED => 'Deprecated',
        E_WARNING, E_USER_WARNING => 'Warning',
        E_NOTICE, E_USER_NOTICE => 'Notice',
        default => 'Diagnostic',
    };
    echo 'handler saw ', $label, ': ', $message, "\n";

    return true;
});

echo "reading a key that is not there:\n";
$value = $row['name'];
echo 'the expression still produced ', var_export($value, true), "\n";

restore_error_handler();

// The usual way to make a warning behave like everything else: promote it.
set_error_handler(static function (int $severity, string $message, string $file, int $line): bool {
    throw new ErrorException($message, 0, $severity, $file, $line);
});

try {
    $parts = str_split('abc', 0);
} catch (ValueError $e) {
    echo 'still a ValueError: ', $e->getMessage(), "\n";
} catch (ErrorException $e) {
    echo 'promoted to ErrorException: ', $e->getMessage(), "\n";
}

try {
    $n = '8 apples' + 1;
} catch (ErrorException $e) {
    echo 'promoted to ErrorException: ', $e->getMessage(), "\n";
}

restore_error_handler();

echo "about to raise something no catch block can see\n";

try {
    trigger_error('the queue driver is gone', E_USER_ERROR);
} catch (Throwable $e) {
    echo "unreachable: a fatal error is not thrown\n";
}

echo "unreachable: the script has already stopped\n";
