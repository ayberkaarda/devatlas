<?php

declare(strict_types=1);

function describe(Throwable $e): string
{
    $message = preg_replace('~,? (?:called )?in .+? on line \d+~', '', $e->getMessage());

    return $e::class . ': ' . $message;
}

/** @param callable():mixed $fn */
function attempt(string $label, callable $fn): void
{
    try {
        $fn();
        printf("%-22s no throw\n", $label);
    } catch (Throwable $e) {
        printf("%-22s %s\n", $label, describe($e));
    }
}

// Throwable is the root. Error and Exception are its two branches, and a
// `catch (Exception)` does not see anything on the Error side.
foreach (['Error', 'Exception', 'TypeError', 'ValueError', 'DivisionByZeroError'] as $class) {
    printf(
        "%-20s Throwable:%-5s Error:%-5s Exception:%s\n",
        $class,
        var_export(is_a($class, Throwable::class, true), true),
        var_export(is_a($class, Error::class, true), true),
        var_export(is_a($class, Exception::class, true), true),
    );
}

function needsTwo(int $a, int $b): int
{
    return $a + $b;
}

attempt('intdiv by zero', static fn () => intdiv(1, 0));
attempt('modulo by zero', static fn () => 1 % 0);
attempt('divide by zero', static fn () => 1 / 0);
attempt('bad argument value', static fn () => array_chunk([1, 2, 3], 0));
attempt('too few arguments', static fn () => needsTwo(1));
attempt('undefined function', static fn () => nosuchfunction());
attempt('method on null', static function () {
    $nothing = null;

    return $nothing->go();
});
attempt('missing class', static fn () => new NoSuchClass());
attempt('bad json', static fn () => json_decode('{', true, 512, JSON_THROW_ON_ERROR));

// Catching only Exception leaves an Error running past you.
try {
    try {
        intdiv(1, 0);
    } catch (Exception $e) {
        echo "the Exception handler ran\n";
    }
} catch (Error $e) {
    echo 'escaped to the Error handler: ', describe($e), "\n";
}
