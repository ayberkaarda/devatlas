<?php

declare(strict_types=1);

// Namespaces are resolved at compile time, before any autoloader is consulted.
// The name the autoloader receives is always fully qualified, without a leading
// separator, whatever the source wrote.

namespace Shop\Catalogue;

use ArrayObject as Bag;
use InvalidArgumentException;

class Product
{
    public function __construct(public readonly string $sku)
    {
    }
}

$requested = [];

\spl_autoload_register(static function (string $class) use (&$requested): void {
    $requested[] = $class;
});

echo __NAMESPACE__, "\n";
echo Product::class, "\n";
echo Bag::class, "\n";
echo InvalidArgumentException::class, "\n";

// An unqualified class name is resolved against the current namespace. A name
// with a leading backslash is global. `use` only rewrites the compiled name.
$product = new Product('SKU-1');
$sameThing = new \Shop\Catalogue\Product('SKU-1');
\var_dump($product::class === $sameThing::class);

$bag = new Bag([1, 2, 3]);
\var_dump($bag::class, \count($bag));

// Functions and constants fall back to the global namespace when the current
// one has no match. Classes never do.
\var_dump(\strtoupper('fallback works for functions'));
\var_dump(\PHP_INT_SIZE > 0);

try {
    new ArrayObject([]);   // resolves to Shop\Catalogue\ArrayObject, not the global one
} catch (\Error $e) {
    echo $e::class, ': ', $e->getMessage(), "\n";
}

echo \implode(' | ', $requested), "\n";

// A string class name carries no namespace context at all: it is taken as
// fully qualified, which is why ::class exists.
$fromString = 'Product';
try {
    new $fromString();
} catch (\Error $e) {
    echo $e::class, ': ', $e->getMessage(), "\n";
}

$correct = Product::class;
$made = new $correct('SKU-2');
echo $made->sku, "\n";

echo \implode(' | ', $requested), "\n";
