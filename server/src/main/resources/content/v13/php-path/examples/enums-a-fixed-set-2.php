<?php

declare(strict_types=1);

// A backed enum adds one scalar per case, for crossing a boundary: a column, a
// query string, a JSON document. The backing value is not the identity.

enum Currency: string
{
    case Euro = 'EUR';
    case Pound = 'GBP';
    case Yen = 'JPY';

    public const Default = self::Euro;

    public function minorUnits(): int
    {
        return $this === self::Yen ? 0 : 2;
    }
}

var_dump(Currency::Euro->value, Currency::Euro->name);
var_dump(Currency::from('GBP') === Currency::Pound);
var_dump(Currency::tryFrom('JPY')?->name);
var_dump(Currency::tryFrom('CHF'));
var_dump(Currency::Default === Currency::Euro);

// from() throws; tryFrom() returns null. Which one you want depends on whether
// an unknown value is a bug or an input.
try {
    Currency::from('CHF');
} catch (ValueError $e) {
    echo $e::class, ': ', $e->getMessage(), "\n";
}

$input = 'CHF';
$currency = Currency::tryFrom($input) ?? Currency::Default;
echo "fell back to {$currency->name}\n";

// A backed enum serialises to its value and nothing else.
echo json_encode(['currency' => Currency::Yen, 'minor' => Currency::Yen->minorUnits()]), "\n";

// json_decode does not reverse that: it gives back the scalar.
$decoded = json_decode('{"currency":"JPY"}', true, 512, JSON_THROW_ON_ERROR);
var_dump(get_debug_type($decoded['currency']));
var_dump(Currency::from($decoded['currency'])->name);

// A pure enum has no value, so from() does not exist on it.
enum Flag
{
    case On;
    case Off;
}

var_dump(Flag::On instanceof BackedEnum, Currency::Euro instanceof BackedEnum);
var_dump((new ReflectionEnum(Flag::class))->getBackingType());
var_dump((string) (new ReflectionEnum(Currency::class))->getBackingType());
