<?php

declare(strict_types=1);

// Which functions and constructs compare loosely, and what to write instead.

$roles = ['admin', 'editor', 'viewer'];

var_dump(in_array(0, $roles));          // loose by default
var_dump(in_array(0, $roles, true));    // strict

$ids = [1, 2, 3];
var_dump(in_array('1', $ids), in_array('1', $ids, true));
var_dump(array_search('1', $ids), array_search('1', $ids, true));

// array_search returns 0 for the first element, and 0 is falsy. The result has
// to be compared with ===, or a hit at index 0 reads as a miss.
$found = array_search(1, $ids, true);
var_dump($found);
echo $found ? "truthy test says: not found\n" : "truthy test says: not found\n";
echo $found !== false ? "identity test says: found\n" : "identity test says: not found\n";

// switch compares loosely; match compares identically.
$input = '1';

switch ($input) {
    case 1:
        echo "switch: took the int 1 branch\n";
        break;
    default:
        echo "switch: took the default branch\n";
}

echo match (true) {
    $input === '1' => "match(true): took the string branch\n",
    default => "match(true): took the default branch\n",
};

try {
    echo match ($input) {
        1 => "match: took the int 1 branch\n",
    };
} catch (\UnhandledMatchError $e) {
    echo $e::class, ': ', $e->getMessage(), "\n";
}

// array_keys has the same flag.
$stock = ['a' => 0, 'b' => '0', 'c' => false];
echo json_encode(array_keys($stock, 0)), "\n";
echo json_encode(array_keys($stock, 0, true)), "\n";
