<?php

declare(strict_types=1);

// The failure this lesson is named after. Three pieces of state outlive a test
// case: a static property, a superglobal, and a process-wide ini setting. Each
// one makes a later test depend on whether an earlier one ran.

final class Config
{
    /** @var array<string, string>|null */
    private static ?array $values = null;

    /** @param array<string, string> $values */
    public static function load(array $values): void
    {
        // The guard is the bug: a second load is silently ignored, so whichever
        // test ran first decides what every later test sees.
        self::$values ??= $values;
    }

    public static function get(string $key): string
    {
        return self::$values[$key] ?? '(unset)';
    }

    public static function forget(): void
    {
        self::$values = null;
    }
}

final class Cart
{
    /** @var list<int> */
    private static array $items = [];

    public static function add(int $cents): void
    {
        self::$items[] = $cents;
    }

    public static function total(): int
    {
        return array_sum(self::$items);
    }
}

/** @param array<string, callable():string> $cases */
function report(string $label, array $cases): void
{
    echo $label, "\n";
    foreach ($cases as $name => $case) {
        printf("  %-26s %s\n", $name, $case());
    }
}

$currencyTest = static function (): string {
    Config::load(['currency' => 'EUR']);

    return Config::get('currency') === 'EUR' ? 'ok' : 'FAIL got ' . Config::get('currency');
};

$localeTest = static function (): string {
    Config::load(['currency' => 'GBP']);

    return Config::get('currency') === 'GBP' ? 'ok' : 'FAIL got ' . Config::get('currency');
};

$cartOneItem = static function (): string {
    Cart::add(100);

    return Cart::total() === 100 ? 'ok' : 'FAIL total is ' . Cart::total();
};

$cartTwoItems = static function (): string {
    Cart::add(100);
    Cart::add(250);

    return Cart::total() === 350 ? 'ok' : 'FAIL total is ' . Cart::total();
};

// Each test passes when it is the only one that ran.
Config::forget();
report('currency test alone', ['currency' => $currencyTest]);
Config::forget();
report('locale test alone', ['locale' => $localeTest]);

// Run together, the second one fails, and which one fails depends on the order.
Config::forget();
report('currency then locale', ['currency' => $currencyTest, 'locale' => $localeTest]);
Config::forget();
report('locale then currency', ['locale' => $localeTest, 'currency' => $currencyTest]);

// The static array in Cart accumulates instead of resetting, so the second
// case fails by exactly the amount the first case added.
report('cart, in order', ['one item' => $cartOneItem, 'two items' => $cartTwoItems]);

// A superglobal is process state too. A test that writes one leaves it written.
$_GET = [];
$adminTest = static function (): string {
    $_GET['admin'] = '1';

    return isset($_GET['admin']) ? 'ok' : 'FAIL';
};
$anonymousTest = static function (): string {
    return isset($_GET['admin']) ? 'FAIL admin leaked in from another test' : 'ok';
};
report('superglobal', ['admin sees the flag' => $adminTest, 'anonymous does not' => $anonymousTest]);

// So is an ini setting. The value is restored here so the rest of the file is
// unaffected; a test suite that forgets is the point being made.
$before = ini_get('serialize_precision');
ini_set('serialize_precision', '3');
$precisionTest = static function (): string {
    return ini_get('serialize_precision') === '3' ? 'ok' : 'FAIL';
};
$defaultTest = static function () use ($before): string {
    return ini_get('serialize_precision') === $before
        ? 'ok'
        : 'FAIL the previous test changed a process-wide setting';
};
report('ini setting', ['sets precision' => $precisionTest, 'expects the default' => $defaultTest]);
ini_set('serialize_precision', (string) $before);
