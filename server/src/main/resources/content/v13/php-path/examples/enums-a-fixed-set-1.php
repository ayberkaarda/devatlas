<?php

declare(strict_types=1);

// A pure enum: a closed set of named cases, each one a single object.

enum Stage
{
    case Draft;
    case Review;
    case Published;

    public function isEditable(): bool
    {
        return match ($this) {
            Stage::Draft, Stage::Review => true,
            Stage::Published => false,
        };
    }

    public function next(): ?self
    {
        return match ($this) {
            Stage::Draft => Stage::Review,
            Stage::Review => Stage::Published,
            Stage::Published => null,
        };
    }
}

// Every reference to a case is the same object, so === is the right test and
// there is no second instance to compare against.
var_dump(Stage::Draft === Stage::Draft);
var_dump(Stage::Draft instanceof Stage, Stage::Draft instanceof UnitEnum);
var_dump(Stage::Draft->name);

foreach (Stage::cases() as $stage) {
    printf("%-10s editable:%-5s next:%s\n",
        $stage->name,
        var_export($stage->isEditable(), true),
        $stage->next()?->name ?? '(none)',
    );
}

// The type declaration is the check. Nothing else can arrive.
function advance(Stage $stage): string
{
    return $stage->next()?->name ?? 'end of the line';
}

echo advance(Stage::Review), "\n";

try {
    // Passing a string where the enum is declared is the whole point here.
    advance('Review');
} catch (TypeError $e) {
    echo $e::class, ': ', preg_replace('~,? (?:called )?in .+? on line \d+~', '', $e->getMessage()), "\n";
}

// A pure enum has no value and no constructor.
try {
    (new ReflectionClass(Stage::class))->newInstance();
} catch (Error $e) {
    echo $e::class, ': ', $e->getMessage(), "\n";
}
