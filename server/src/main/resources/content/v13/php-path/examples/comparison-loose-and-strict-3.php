<?php

declare(strict_types=1);

// Ordering: the spaceship operator, the stability PHP 8.0 guarantees, and the
// one comparison that should never be an equality test.

$rows = [
    ['name' => 'ana', 'score' => 3],
    ['name' => 'bo', 'score' => 1],
    ['name' => 'cy', 'score' => 3],
    ['name' => 'di', 'score' => 1],
];

usort($rows, static fn (array $l, array $r): int => $l['score'] <=> $r['score']);

// Sorts are stable since PHP 8.0: equal elements keep their input order, so
// this line is a specified result rather than one run's accident.
echo implode(',', array_column($rows, 'name')), "\n";

// A two-key comparison, written as a chain of spaceships.
usort($rows, static fn (array $l, array $r): int
    => [$r['score'], $l['name']] <=> [$l['score'], $r['name']]);
echo implode(',', array_column($rows, 'name')), "\n";

var_dump(1 <=> 2, 2 <=> 2, 3 <=> 2);
var_dump('abc' <=> 'abd');
var_dump([1, 2] <=> [1, 2]);

// Floats. Printing the digits would depend on the precision ini setting, so the
// listing prints the comparison instead.
$sum = 0.1 + 0.2;
var_dump($sum == 0.3);
var_dump($sum > 0.3);
var_dump(abs($sum - 0.3) < PHP_FLOAT_EPSILON);
var_dump(round($sum, 10) == round(0.3, 10));

// String comparison is byte-wise, which is not alphabetical order.
$names = ['delta', 'Echo', 'alpha'];
sort($names);
echo implode(',', $names), "
";
sort($names, SORT_NATURAL | SORT_FLAG_CASE);
echo implode(',', $names), "
";
var_dump('Echo' <=> 'alpha', strcasecmp('Echo', 'alpha'));
