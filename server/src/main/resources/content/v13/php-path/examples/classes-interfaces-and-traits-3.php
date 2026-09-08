<?php

declare(strict_types=1);

function describe(Throwable $e): string
{
    $message = preg_replace('~,? (?:called )?in .+? on line \d+~', '', $e->getMessage());

    return $e::class . ': ' . $message;
}

// The shape that survives a single-inheritance language: an interface for the
// type, a trait for the shared body, a final class for the thing itself.

interface Auditable
{
    public function auditLine(): string;
}

trait AuditsByName
{
    abstract public function name(): string;

    public function auditLine(): string
    {
        return static::class . ':' . $this->name();
    }
}

abstract class Record implements Auditable
{
    use AuditsByName;

    protected function checksum(): int
    {
        return strlen($this->name());
    }
}

final class Invoice extends Record
{
    public function __construct(private readonly string $number)
    {
    }

    public function name(): string
    {
        return $this->number;
    }

    public function summary(): string
    {
        return $this->auditLine() . '/' . $this->checksum();
    }
}

$invoice = new Invoice('INV-42');
echo $invoice->summary(), "\n";
var_dump($invoice instanceof Auditable, $invoice instanceof Record);

try {
    // Reflection is the only way to reach `new Record()` at run time; writing
    // it literally would be a compile-time fatal instead of a catchable Error.
    $bad = new ReflectionClass(Record::class);
    $bad->newInstance();
} catch (Error $e) {
    echo describe($e), "\n";
}

try {
    $invoice->checksum();
} catch (Error $e) {
    echo describe($e), "\n";
}

final class Secretive
{
    private string $token = 'hidden';
}

try {
    echo (new Secretive())->token;
} catch (Error $e) {
    echo describe($e), "\n";
}

// static:: resolves at call time; self:: resolves where it is written.
class Reporter
{
    public static function make(): static
    {
        return new static();
    }

    public static function makeSelf(): self
    {
        return new self();
    }
}

final class SubReporter extends Reporter
{
}

echo SubReporter::make()::class, ' ', SubReporter::makeSelf()::class, "\n";
