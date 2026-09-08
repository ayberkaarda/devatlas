<?php

declare(strict_types=1);

// The same behaviour, with the shared state turned into an argument. Nothing is
// static, so a test cannot leave anything behind, and the suite gives the same
// answer in any order.

interface Clock
{
    public function today(): string;
}

final class FixedClock implements Clock
{
    public function __construct(private readonly string $date)
    {
    }

    public function today(): string
    {
        return $this->date;
    }
}

final class Config
{
    /** @param array<string, string> $values */
    public function __construct(private readonly array $values)
    {
    }

    public function get(string $key): string
    {
        return $this->values[$key] ?? '(unset)';
    }
}

final class Cart
{
    /** @var list<int> */
    private array $items = [];

    public function add(int $cents): void
    {
        $this->items[] = $cents;
    }

    public function total(): int
    {
        return array_sum($this->items);
    }
}

final class Receipt
{
    public function __construct(
        private readonly Config $config,
        private readonly Clock $clock,
    ) {
    }

    public function render(Cart $cart): string
    {
        return sprintf(
            '%s %s %d',
            $this->clock->today(),
            $this->config->get('currency'),
            $cart->total(),
        );
    }
}

/** @param array<string, callable():string> $cases */
function report(string $label, array $cases): void
{
    echo $label, "\n";
    foreach ($cases as $name => $case) {
        printf("  %-22s %s\n", $name, $case());
    }
}

$euroReceipt = static function (): string {
    $receipt = new Receipt(new Config(['currency' => 'EUR']), new FixedClock('2024-03-01'));
    $cart = new Cart();
    $cart->add(100);

    return $receipt->render($cart) === '2024-03-01 EUR 100' ? 'ok' : 'FAIL';
};

$poundReceipt = static function (): string {
    $receipt = new Receipt(new Config(['currency' => 'GBP']), new FixedClock('2024-03-02'));
    $cart = new Cart();
    $cart->add(100);
    $cart->add(250);

    return $receipt->render($cart) === '2024-03-02 GBP 350' ? 'ok' : 'FAIL';
};

$cases = ['euro' => $euroReceipt, 'pound' => $poundReceipt];

report('declaration order', $cases);
report('reversed', array_reverse($cases, true));

// Order independence is a property that can be asserted, by running the same
// cases in several orders and comparing the outcomes rather than printing them.
$orders = [
    ['euro', 'pound'],
    ['pound', 'euro'],
    ['euro', 'euro', 'pound'],
];
$outcomes = [];
foreach ($orders as $order) {
    $line = [];
    foreach ($order as $name) {
        $line[$name] = $cases[$name]();
    }
    $outcomes[] = $line;
}
$allOk = array_reduce(
    $outcomes,
    static fn (bool $carry, array $line): bool => $carry && array_unique(array_values($line)) === ['ok'],
    true,
);
var_dump($allOk);

// The clock seam is what keeps the date out of the assertion. Reading the real
// clock inside render() would make the expected value depend on the day the
// suite ran, which is the same class of bug as the static.
$fixed = new FixedClock('2024-03-01');
var_dump($fixed->today() === $fixed->today());

// A test double is just another implementation of the interface.
final class RecordingClock implements Clock
{
    public int $calls = 0;

    public function today(): string
    {
        ++$this->calls;

        return '2024-01-01';
    }
}

$recording = new RecordingClock();
$receipt = new Receipt(new Config(['currency' => 'JPY']), $recording);
$cart = new Cart();
$cart->add(500);
echo $receipt->render($cart), "\n";
echo $receipt->render($cart), "\n";
var_dump($recording->calls);
