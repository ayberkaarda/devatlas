<?php

declare(strict_types=1);

// An interface is the type. A class may implement any number of them, and
// implementing one is a declaration the engine checks at compile time.

interface Priced
{
    public function amountInCents(): int;
}

interface Describable
{
    public function label(): string;
}

final class Book implements Priced, Describable
{
    public function __construct(
        private readonly string $title,
        private readonly int $cents,
    ) {
    }

    public function amountInCents(): int
    {
        return $this->cents;
    }

    public function label(): string
    {
        return "book: {$this->title}";
    }
}

final class Shipping implements Priced
{
    public function __construct(private readonly int $cents)
    {
    }

    public function amountInCents(): int
    {
        return $this->cents;
    }
}

/** @param list<Priced> $lines */
function total(array $lines): int
{
    return array_sum(array_map(static fn (Priced $l): int => $l->amountInCents(), $lines));
}

$lines = [new Book('Small Gods', 1299), new Shipping(450)];

var_dump(total($lines));

foreach ($lines as $line) {
    printf(
        "%-24s Priced:%s Describable:%s\n",
        $line::class,
        var_export($line instanceof Priced, true),
        var_export($line instanceof Describable, true),
    );
}

// An interface is a type, so it can be narrowed at run time.
foreach ($lines as $line) {
    echo $line instanceof Describable ? $line->label() : 'no label', "\n";
}

echo implode(',', class_implements(Book::class)), "\n";
