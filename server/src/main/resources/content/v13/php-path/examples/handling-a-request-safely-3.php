<?php

declare(strict_types=1);

// SQL. The database used here is SQLite in memory, so the listing runs without
// a server, and the lesson is about PDO rather than about any one engine.
//
// The driver's own message text belongs to SQLite and not to PHP 8.2, so the
// listing prints the SQLSTATE code instead of the sentence that came with it.

$pdo = new PDO('sqlite::memory:', null, null, [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_EMULATE_PREPARES => false,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
]);

$pdo->exec('CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT NOT NULL, role TEXT NOT NULL)');

$insert = $pdo->prepare('INSERT INTO users (name, role) VALUES (:name, :role)');
foreach ([['ana', 'admin'], ['bo', 'editor'], ['cy', 'viewer']] as [$name, $role]) {
    $insert->execute(['name' => $name, 'role' => $role]);
}

$input = "ana' OR '1'='1";

// Concatenation. The quote in the input ends the literal and the rest of the
// string becomes SQL. Nothing raises: the query succeeds and returns too much.
$concatenated = "SELECT name FROM users WHERE name = '{$input}'";
$rows = $pdo->query($concatenated)->fetchAll();
printf("concatenated: %d rows for a table of 3\n", count($rows));

// The same input through a placeholder is a value, so it matches no row.
$prepared = $pdo->prepare('SELECT name FROM users WHERE name = :name');
$prepared->execute(['name' => $input]);
printf("prepared: %d rows\n", count($prepared->fetchAll()));

$prepared->execute(['name' => 'ana']);
printf("prepared with a real name: %d rows\n", count($prepared->fetchAll()));

// A placeholder is a value and never an identifier or a keyword. A column name
// has to come from an allow-list.
$requested = 'role; DROP TABLE users';
$allowed = ['name', 'role'];
$column = in_array($requested, $allowed, true) ? $requested : 'name';
$sorted = $pdo->query("SELECT name FROM users ORDER BY {$column} ASC")->fetchAll();
echo 'sorted by ', $column, ': ', implode(',', array_column($sorted, 'name')), "\n";

// Placeholders cannot be interpolated into an IN list either. The list of
// placeholders is generated, and only the count comes from the input.
$wanted = ['ana', 'cy'];
$marks = implode(',', array_fill(0, count($wanted), '?'));
$in = $pdo->prepare("SELECT name FROM users WHERE name IN ({$marks}) ORDER BY name");
$in->execute($wanted);
echo 'in list: ', implode(',', array_column($in->fetchAll(), 'name')), "\n";

// Errors surface as exceptions because ERRMODE_EXCEPTION was set. Without it
// PDO would return false and the next line would work on a non-object.
try {
    $pdo->query('SELECT * FROM missing_table');
} catch (PDOException $e) {
    echo $e::class, ' SQLSTATE ', $e->getCode(), "\n";
}

try {
    $bad = $pdo->prepare('SELECT name FROM users WHERE name = :name');
    $bad->execute(['nope' => 'x']);
} catch (PDOException $e) {
    echo $e::class, ' SQLSTATE ', $e->getCode(), "\n";
}

// A transaction rolls the whole unit back, including the row that succeeded.
try {
    $pdo->beginTransaction();
    $insert->execute(['name' => 'di', 'role' => 'viewer']);
    $pdo->prepare('INSERT INTO users (name, role) VALUES (:name, :role)')
        ->execute(['name' => 'ed', 'role' => null]);
    $pdo->commit();
} catch (PDOException $e) {
    $pdo->rollBack();
    echo 'rolled back, SQLSTATE ', $e->getCode(), "\n";
}

printf("rows after rollback: %d\n", (int) $pdo->query('SELECT COUNT(*) AS c FROM users')->fetch()['c']);
