<?php

declare(strict_types=1);

// finally, chaining, and the two ways a rethrow loses information.

final class ImportFailed extends RuntimeException
{
    // The names avoid $file, $line, $message and $code: those are declared on
    // Exception already, and redeclaring one as readonly is a fatal error.
    public function __construct(
        public readonly string $source,
        public readonly int $rowNumber,
        ?Throwable $previous = null,
    ) {
        parent::__construct("row {$rowNumber} of {$source} could not be imported", 0, $previous);
    }
}

function order(): array
{
    $steps = [];
    try {
        $steps[] = 'try';
        throw new LogicException('boom');
    } catch (LogicException $e) {
        $steps[] = 'catch';
    } finally {
        $steps[] = 'finally';
    }
    $steps[] = 'after';

    return $steps;
}

echo implode(' -> ', order()), "\n";

// A return inside finally replaces the value the try block was returning, and
// it also swallows an exception that was on its way out.
function returnsFromTry(): string
{
    try {
        return 'from try';
    } finally {
        // no return here
    }
}

function returnsFromFinally(): string
{
    try {
        return 'from try';
    } finally {
        return 'from finally';
    }
}

function swallows(): string
{
    try {
        throw new RuntimeException('never seen');
    } finally {
        return 'finally won';
    }
}

echo returnsFromTry(), ' / ', returnsFromFinally(), ' / ', swallows(), "\n";

// Chaining keeps the cause. Rethrowing a new exception without $previous
// discards it, and that is how a stack trace stops naming the real fault.
function parseAmount(string $raw): int
{
    $value = filter_var($raw, FILTER_VALIDATE_INT);
    if ($value === false) {
        throw new InvalidArgumentException("not an integer: {$raw}");
    }

    return $value;
}

function importRow(string $raw, int $line): int
{
    try {
        return parseAmount($raw);
    } catch (InvalidArgumentException $e) {
        throw new ImportFailed('amounts.csv', $line, $e);
    }
}

try {
    importRow('12x', 7);
} catch (ImportFailed $e) {
    echo $e::class, ': ', $e->getMessage(), "\n";
    for ($cause = $e->getPrevious(); $cause !== null; $cause = $cause->getPrevious()) {
        echo '  caused by ', $cause::class, ': ', $cause->getMessage(), "\n";
    }
    echo '  source: ', $e->source, ' row: ', $e->rowNumber, "\n";
}

// Catching several types with one block, and the union catch.
foreach ([new TypeError('t'), new ValueError('v'), new RuntimeException('r')] as $thrown) {
    try {
        throw $thrown;
    } catch (TypeError | ValueError $e) {
        echo 'argument problem: ', $e::class, "\n";
    } catch (Throwable $e) {
        echo 'something else: ', $e::class, "\n";
    }
}
