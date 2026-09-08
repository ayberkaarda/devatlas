<?php

declare(strict_types=1);

function describe(Throwable $e): string
{
    $message = preg_replace('~,? (?:called )?in .+? on line \d+~', '', $e->getMessage());

    return $e::class . ': ' . $message;
}

class Draft
{
    public string $title;          // typed, and deliberately not initialised
    public ?string $subtitle = null;
    public int $words = 0;
}

// A readonly class marks every declared property readonly at once (PHP 8.2).
readonly class Money
{
    public function __construct(
        public int $amount,
        public string $currency,
    ) {
    }

    public function plus(self $other): self
    {
        if ($other->currency !== $this->currency) {
            throw new InvalidArgumentException('currency mismatch');
        }

        return new self($this->amount + $other->amount, $this->currency);
    }
}

$draft = new Draft();
var_dump($draft->subtitle, $draft->words);

try {
    echo $draft->title;
} catch (Error $e) {
    echo describe($e), "\n";
}

$draft->title = 'A first pass';
var_dump($draft->title);

try {
    $draft->words = '12';
} catch (TypeError $e) {
    echo describe($e), "\n";
}

$five = new Money(500, 'EUR');
$eight = $five->plus(new Money(300, 'EUR'));
var_dump($five->amount, $eight->amount);

try {
    $five->amount = 1;
} catch (Error $e) {
    echo describe($e), "\n";
}

try {
    $five->plus(new Money(300, 'USD'));
} catch (InvalidArgumentException $e) {
    echo describe($e), "\n";
}
