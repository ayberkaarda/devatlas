<?php

declare(strict_types=1);

// Escaping is a property of the destination, not of the value. The same string
// needs a different transformation for HTML text, an HTML attribute, a URL and
// a JSON document, and using one where another belongs is the whole bug.

$name = 'Tom & "Jerry"';
$attack = '" onmouseover="steal()';
$path = 'reports/Q3 2024/summary.pdf';

// Since PHP 8.1 the default flags of htmlspecialchars are
// ENT_QUOTES | ENT_SUBSTITUTE | ENT_HTML401, so single quotes are escaped
// without asking. In PHP 8.0 and earlier the default left them alone.
echo htmlspecialchars($name), "\n";
echo htmlspecialchars("it's here"), "\n";
echo htmlspecialchars("it's here", ENT_COMPAT), "\n";

// An attribute value that is not quoted cannot be made safe by escaping.
$good = '<a title="' . htmlspecialchars($attack, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . '">x</a>';
$bad = '<a title=' . htmlspecialchars($attack, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8') . '>x</a>';
echo $good, "\n";
echo $bad, "\n";

// A URL path segment is not HTML. rawurlencode is the transformation, and
// htmlspecialchars is not a substitute for it.
echo rawurlencode($path), "\n";
echo htmlspecialchars($path), "\n";
echo '/files/' . implode('/', array_map('rawurlencode', explode('/', $path))), "\n";

// Escaping twice produces visibly wrong output rather than an error, which is
// why it survives review.
$once = htmlspecialchars($name);
$twice = htmlspecialchars($once);
echo $twice, "\n";
var_dump($once === htmlspecialchars($once, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8', false));

// Invalid UTF-8. Without ENT_SUBSTITUTE the function returns an empty string,
// which silently deletes the whole field. The bytes of the substituted result
// are not printed here: the property being demonstrated is the length.
$broken = "valid\xC3\x28broken";
$dropped = htmlspecialchars($broken, ENT_QUOTES, 'UTF-8');
$substituted = htmlspecialchars($broken, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
printf(
    "input:%d dropped:%d substituted:%d valid-utf8:%s\n",
    strlen($broken),
    strlen($dropped),
    strlen($substituted),
    var_export(mb_check_encoding($broken, 'UTF-8'), true),
);

// JSON is a fourth context. The flags matter when the document is embedded in
// a page rather than sent as a response body.
$payload = ['name' => $name, 'note' => '</script><script>x</script>'];
echo json_encode($payload, JSON_THROW_ON_ERROR), "\n";
echo json_encode($payload, JSON_HEX_TAG | JSON_HEX_AMP | JSON_HEX_APOS | JSON_HEX_QUOT | JSON_THROW_ON_ERROR), "\n";

// json_encode refuses malformed UTF-8 rather than producing a broken document.
try {
    json_encode(['x' => $broken], JSON_THROW_ON_ERROR);
} catch (JsonException $e) {
    echo 'JsonException code ', $e->getCode(), ' equals JSON_ERROR_UTF8: ',
        var_export($e->getCode() === JSON_ERROR_UTF8, true), "\n";
}
