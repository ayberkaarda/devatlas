<?php

declare(strict_types=1);

// Everything that arrives in $_GET, $_POST and $_COOKIE is a string or an
// array of strings. There is no type information in an HTTP request, so the
// boundary is where types are established. This listing fills the superglobal
// itself rather than reading one from a web server, so it runs from the CLI.

$_GET = [
    'page' => '2',
    'perPage' => '25 ',
    'email' => 'ana@example.com',
    'sort' => 'name',
    'admin' => 'yes',
    'tags' => ['php', 'forms'],
];

/**
 * @param array<string, mixed> $source
 * @return array{page: int, perPage: int, email: string, sort: string, admin: bool}
 */
function readQuery(array $source): array
{
    $page = filter_var($source['page'] ?? null, FILTER_VALIDATE_INT, [
        'options' => ['default' => 1, 'min_range' => 1],
    ]);
    $perPage = filter_var($source['perPage'] ?? null, FILTER_VALIDATE_INT, [
        'options' => ['default' => 10, 'min_range' => 1, 'max_range' => 100],
    ]);
    $email = filter_var($source['email'] ?? null, FILTER_VALIDATE_EMAIL);
    if ($email === false) {
        throw new InvalidArgumentException('email is not a valid address');
    }

    // An allow-list, not an escape. A sort column is chosen, never quoted.
    $allowed = ['name', 'created', 'price'];
    $sort = in_array($source['sort'] ?? '', $allowed, true) ? $source['sort'] : 'name';

    // FILTER_VALIDATE_BOOL with FILTER_NULL_ON_FAILURE tells "absent" from
    // "present and not a boolean".
    $admin = filter_var($source['admin'] ?? null, FILTER_VALIDATE_BOOL, FILTER_NULL_ON_FAILURE);

    return [
        'page' => $page,
        'perPage' => $perPage,
        'email' => $email,
        'sort' => $sort,
        'admin' => $admin === true,
    ];
}

$query = readQuery($_GET);
echo json_encode($query), "\n";

// What each validator does with hostile or malformed values.
$cases = ['12', ' 12 ', '12abc', '', '0', '-3', '1e3', '99999999999999999999'];
foreach ($cases as $case) {
    printf(
        "%-22s int:%-8s bool:%-6s\n",
        var_export($case, true),
        var_export(filter_var($case, FILTER_VALIDATE_INT), true),
        var_export(filter_var($case, FILTER_VALIDATE_BOOL, FILTER_NULL_ON_FAILURE), true),
    );
}

// A URL is only "valid" in the syntactic sense. A scheme allow-list is the
// check that matters.
$urls = ['https://example.com/x', 'javascript:alert(1)', 'data:text/html,<script>', 'ftp://example.com'];
foreach ($urls as $url) {
    $syntactic = filter_var($url, FILTER_VALIDATE_URL) !== false;
    $scheme = strtolower((string) parse_url($url, PHP_URL_SCHEME));
    $safe = $syntactic && in_array($scheme, ['http', 'https'], true);
    printf("%-28s syntactic:%-6s scheme:%-11s accepted:%s\n",
        $url, var_export($syntactic, true), $scheme, var_export($safe, true));
}

// A submitted password is never stored and never compared with ===. The hash
// contains its own random salt, so the digest itself is not reproducible; what
// is reproducible is the verification result.
$hash = password_hash('correct horse', PASSWORD_DEFAULT);
var_dump(password_verify('correct horse', $hash));
var_dump(password_verify('Correct Horse', $hash));
var_dump(password_hash('correct horse', PASSWORD_DEFAULT) === $hash);
var_dump(password_needs_rehash($hash, PASSWORD_DEFAULT));
var_dump(password_get_info($hash)['algoName']);
