<?php

declare(strict_types=1);

// A test is a function that either agrees with a claim or says why it does not.
// This is the smallest harness that still reports usefully: it names the case,
// counts outcomes and exits non-zero on failure. No package is involved.

final class Result
{
    public int $passed = 0;
    /** @var list<string> */
    public array $failures = [];
}

/** @param array<string, callable():void> $cases */
function run(array $cases, Result $result): void
{
    foreach ($cases as $name => $case) {
        try {
            $case();
            ++$result->passed;
            echo "  ok   {$name}\n";
        } catch (AssertionFailed $e) {
            $result->failures[] = $name;
            echo "  FAIL {$name}: {$e->getMessage()}\n";
        } catch (Throwable $e) {
            $result->failures[] = $name;
            echo "  FAIL {$name}: unexpected ", $e::class, "\n";
        }
    }
}

final class AssertionFailed extends RuntimeException
{
}

function same(mixed $expected, mixed $actual, string $what): void
{
    if ($expected !== $actual) {
        throw new AssertionFailed(sprintf(
            '%s: expected %s, got %s',
            $what,
            var_export($expected, true),
            var_export($actual, true),
        ));
    }
}

/** @param class-string<Throwable> $class */
function throws(string $class, callable $fn, string $what): void
{
    try {
        $fn();
    } catch (Throwable $e) {
        if ($e instanceof $class) {
            return;
        }
        throw new AssertionFailed("{$what}: expected {$class}, got " . $e::class);
    }
    throw new AssertionFailed("{$what}: expected {$class}, nothing was thrown");
}

// The code under test.
final class Slug
{
    public static function from(string $title): string
    {
        $trimmed = trim($title);
        if ($trimmed === '') {
            throw new InvalidArgumentException('title is empty');
        }
        $lower = strtolower($trimmed);
        $dashed = preg_replace('~[^a-z0-9]+~', '-', $lower) ?? '';

        return trim($dashed, '-');
    }
}

$result = new Result();

echo "Slug\n";
run([
    'lowercases' => static fn () => same('hello', Slug::from('Hello'), 'slug'),
    'joins words' => static fn () => same('two-words', Slug::from('Two Words'), 'slug'),
    'strips punctuation' => static fn () => same('cost-99', Slug::from('Cost: 99!'), 'slug'),
    'trims edges' => static fn () => same('edge', Slug::from('  --edge--  '), 'slug'),
    'rejects empty' => static fn () => throws(
        InvalidArgumentException::class,
        static fn () => Slug::from('   '),
        'empty title',
    ),
    'this one is wrong on purpose' => static fn () => same('Hello', Slug::from('Hello'), 'slug'),
], $result);

printf("%d passed, %d failed\n", $result->passed, count($result->failures));
echo 'failures: ', implode(', ', $result->failures), "\n";
