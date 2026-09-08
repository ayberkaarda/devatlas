<?php

declare(strict_types=1);

function describe(Throwable $e): string
{
    $message = preg_replace('~,? (?:called )?in .+? on line \d+~', '', $e->getMessage());

    return $e::class . ': ' . $message;
}

// What an enum is not: it is not a class you can add state to, and it is not
// an integer you can use as an array key.

interface HasLabel
{
    public function label(): string;
}

enum Priority: int implements HasLabel
{
    case Low = 1;
    case Normal = 2;
    case High = 3;

    public function label(): string
    {
        return ucfirst(strtolower($this->name));
    }

    public static function atLeast(self $floor): array
    {
        return array_values(array_filter(
            self::cases(),
            static fn (self $c): bool => $c->value >= $floor->value,
        ));
    }
}

var_dump(Priority::High instanceof HasLabel);
echo implode(',', array_map(
    static fn (Priority $p): string => $p->label(),
    Priority::atLeast(Priority::Normal),
)), "\n";

// cases() returns them in declaration order, which is a specified property of
// the enum rather than a property of this run.
echo implode(',', array_column(Priority::cases(), 'name')), "\n";

// An enum case cannot be an array key: keys are int or string only.
$counts = [];
try {
    $counts[Priority::High] = 1;
} catch (TypeError $e) {
    echo describe($e), "\n";
}

// The backing value can be, and an SplObjectStorage can hold the case itself.
$counts[Priority::High->value] = 1;
echo json_encode($counts), "\n";

$byCase = new SplObjectStorage();
$byCase[Priority::High] = 'urgent';
var_dump($byCase[Priority::High], count($byCase));

// An enum has no writable state. There is no property to set.
$case = Priority::Low;
try {
    $case->value = 9;
} catch (Error $e) {
    echo describe($e), "\n";
}

// Two enums with the same backing value are still different types.
enum Level: int
{
    case Low = 1;
}
var_dump(Priority::Low === Level::Low, Priority::Low->value === Level::Low->value);

// An exhaustive match over cases fails loudly when a case is added later.
function urgency(Priority $p): string
{
    return match ($p) {
        Priority::Low => 'later',
        Priority::Normal => 'today',
        Priority::High => 'now',
    };
}
echo urgency(Priority::Normal), "\n";
