<?php

declare(strict_types=1);

// Nothing loads a class by magic. When the engine meets a name it does not
// know, it calls the registered autoloaders in order until one of them defines
// the class, and then gives up with an Error.

$asked = [];

spl_autoload_register(static function (string $class) use (&$asked): void {
    $asked[] = "first saw {$class}";
});

spl_autoload_register(static function (string $class) use (&$asked): void {
    $asked[] = "second saw {$class}";
    if ($class === 'Reports\Monthly') {
        // A real autoloader would require a file here. This one defines the
        // class inline so the listing stays a single file.
        eval('namespace Reports; class Monthly { public function title(): string { return "monthly"; } }');
    }
});

var_dump(count(spl_autoload_functions()));

// A class the engine already knows never reaches an autoloader.
var_dump(class_exists(ArrayObject::class));
echo count($asked), " autoloader calls so far\n";

$report = new Reports\Monthly();
echo $report->title(), "\n";

foreach ($asked as $line) {
    echo $line, "\n";
}

// The second reference is already resolved, so no autoloader runs again.
$again = new Reports\Monthly();
echo count($asked), " autoloader calls in total\n";

// class_exists() triggers autoloading by default; pass false to ask only about
// what is already loaded.
var_dump(class_exists('Reports\Quarterly', false));
$before = count($asked);
var_dump(class_exists('Reports\Quarterly'));
printf("asking with autoloading added %d calls\n", count($asked) - $before);

// When no autoloader defines it, the engine throws.
try {
    new Reports\Quarterly();
} catch (Error $e) {
    echo $e::class, ': ', $e->getMessage(), "\n";
}

// Interfaces, traits and enums go through the same hook.
var_dump(interface_exists('Reports\Renderable', false));
$before = count($asked);
interface_exists('Reports\Renderable');
printf("interface_exists asked %d autoloaders\n", count($asked) - $before);
