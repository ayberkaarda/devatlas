<?php

declare(strict_types=1);

// A PSR-4 style autoloader: a namespace prefix is mapped to a base directory,
// and the rest of the class name becomes the path under it. This listing
// creates a small tree in the system temp directory, uses it, and removes it.
// Only the path relative to that base is printed, so the recorded output does
// not name this machine.

$root = sys_get_temp_dir() . DIRECTORY_SEPARATOR . 'php-path-autoload-' . bin2hex(random_bytes(4));

/** @param array<string, string> $files */
function writeTree(string $root, array $files): void
{
    foreach ($files as $relative => $contents) {
        $path = $root . DIRECTORY_SEPARATOR . str_replace('/', DIRECTORY_SEPARATOR, $relative);
        if (!is_dir(dirname($path))) {
            mkdir(dirname($path), 0777, true);
        }
        file_put_contents($path, $contents);
    }
}

function removeTree(string $root): void
{
    $items = new RecursiveIteratorIterator(
        new RecursiveDirectoryIterator($root, FilesystemIterator::SKIP_DOTS),
        RecursiveIteratorIterator::CHILD_FIRST,
    );
    foreach ($items as $item) {
        $item->isDir() ? rmdir($item->getPathname()) : unlink($item->getPathname());
    }
    rmdir($root);
}

writeTree($root, [
    'src/Invoice.php' => "<?php\nnamespace Billing;\nclass Invoice { public function kind(): string { return 'invoice'; } }\n",
    'src/Tax/Rate.php' => "<?php\nnamespace Billing\\Tax;\nclass Rate { public function kind(): string { return 'rate'; } }\n",
]);

$prefix = 'Billing\\';
$base = $root . DIRECTORY_SEPARATOR . 'src' . DIRECTORY_SEPARATOR;

$tried = [];

spl_autoload_register(static function (string $class) use ($prefix, $base, &$tried): void {
    if (!str_starts_with($class, $prefix)) {
        $tried[] = "{$class}: not mine";

        return;
    }
    $relative = substr($class, strlen($prefix));
    $file = $base . str_replace('\\', DIRECTORY_SEPARATOR, $relative) . '.php';
    $shown = 'src/' . str_replace('\\', '/', $relative) . '.php';
    if (is_file($file)) {
        $tried[] = "{$class}: loading {$shown}";
        require $file;

        return;
    }
    $tried[] = "{$class}: no file at {$shown}";
});

try {
    $invoice = new Billing\Invoice();
    $rate = new Billing\Tax\Rate();
    echo $invoice->kind(), ' ', $rate->kind(), "\n";

    try {
        new Billing\Refund();
    } catch (Error $e) {
        echo $e::class, ': ', $e->getMessage(), "\n";
    }

    try {
        new Shipping\Label();
    } catch (Error $e) {
        echo $e::class, ': ', $e->getMessage(), "\n";
    }

    foreach ($tried as $line) {
        echo $line, "\n";
    }

    // The mapping is a convention the autoloader implements. Nothing in the
    // engine ties a class name to a file name.
    $reflection = new ReflectionClass(Billing\Invoice::class);
    var_dump($reflection->getShortName(), $reflection->getNamespaceName());
} finally {
    removeTree($root);
    var_dump(is_dir($root));
}
