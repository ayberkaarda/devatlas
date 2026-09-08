## Why this exists

A modern PHP file names classes it never includes. There is no `require` at the top and the code
still runs, which looks like magic and is regularly explained as "Composer handles it". That
explanation stops one step too early, because the mechanism belongs to PHP and not to any tool:
`spl_autoload_register()` is a callback list the engine consults when it meets a class name it does
not know. Composer's job is to generate one such callback from a project's declared namespace
mapping; it plugs into the same hook anyone can register in three lines.

Knowing where the hook is matters the moment something goes wrong, because a class that will not
load produces a diagnostic about the *name*, and the name PHP asked for is often not the name the
source wrote.

## The idea

Autoloading is a switchboard. When a caller asks for a name the exchange does not recognise, it
puts the request to each operator in turn until one of them connects it. Each operator knows a
different part of the directory; none of them knows the whole thing; and if the last one shrugs,
the caller gets a recorded message.

### Where the analogy breaks

The operators are asked in registration order and each one either defines the class or does
nothing — there is no return value and no way to say "not mine, stop asking". Order therefore
decides which of two operators wins, and a slow or careless one is consulted on every miss.

The recorded message is also more specific than a busy tone. It is an `Error`, catchable like any
other, and it names the fully qualified class the *engine* asked for:

```
Error: Class "Shop\Catalogue\ArrayObject" not found
```

That is what an unqualified `new ArrayObject([])` inside `namespace Shop\Catalogue` resolves to.
The switchboard was never asked for the global class, so no operator could have connected it.

Finally, the exchange remembers. Once a class is defined, no autoloader is consulted for it again,
and a class the engine already knows — anything built in — never reaches one at all.

## How it works

Registration takes a callable and nothing else. The engine passes it one fully qualified name,
without a leading separator, whatever the calling source wrote.

```php
spl_autoload_register(static function (string $class): void {
    if (!str_starts_with($class, 'Billing\\')) {
        return;                       // not this autoloader's namespace
    }
    $relative = substr($class, strlen('Billing\\'));
    $file = __DIR__ . '/src/' . str_replace('\\', DIRECTORY_SEPARATOR, $relative) . '.php';
    if (is_file($file)) {
        require $file;
    }
});
```

That is the whole of the PSR-4 convention: a namespace prefix, a base directory, and the remaining
segments as a path. Nothing in the engine ties a class name to a file name; the mapping exists only
inside callbacks like this one.

`class_exists()` triggers the chain by default and takes a second argument to suppress it, which
makes it the tool for asking what is already loaded. `interface_exists()`, `trait_exists()` and
`enum_exists()` go through the same hook.

Name resolution happens first, at compile time, and it has two rules worth memorising. An
unqualified class name is resolved against the current namespace and never falls back to the
global one — unlike a function or constant name, which does. And a class name held in a *string*
carries no namespace context at all:

```
Error: Class "Product" not found
```

`Product::class` produces the fully qualified name and is the reason that constant exists.

## Common mistakes

**Expecting a class to fall back to the global namespace.** Inside a namespace, `new ArrayObject()`
means the one in *your* namespace. Import it with `use` or write `\ArrayObject`.

**Building a class name by concatenating strings.** `new ('App\\Models\\' . $name)` skips every
`use` statement in the file, which is fine as long as the string is fully qualified — and silently
wrong the moment someone abbreviates it.

**Registering an autoloader that answers for names it does not own.** A callback that `require`s a
guessed path for every miss turns one unresolved class into a file-not-found somewhere unrelated.
Return early unless the prefix matches.

**Assuming the autoloader ran because the class loaded.** It runs once per name. A second `new` of
the same class consults nobody, so timing an autoloader by calling it twice measures nothing.

## Check yourself

<details><summary>How many arguments does the engine pass to an autoloader, and what is in them?</summary>

One: the fully qualified class, interface, trait or enum name, with no leading backslash. The
callback returns nothing; defining the class is how it reports success.

</details>

<details><summary>Two autoloaders are registered and both could load <code>App\Thing</code>. Which wins?</summary>

The one registered first, because the chain stops as soon as the class is defined. The second is
never consulted for that name again.

</details>

<details><summary>Why does <code>::class</code> exist when the class name is already a string?</summary>

Because a literal string is taken as fully qualified, while `Product::class` is resolved at compile
time against the current namespace and any `use` statements. The two are the same only when the
source already wrote the full name.

</details>

## Listings

1. `autoloading-and-where-a-class-comes-from-1.php` — the callback chain, what triggers it and
   what does not, and the `Error` at the end of it.
2. `autoloading-and-where-a-class-comes-from-2.php` — a PSR-4 style autoloader over a real
   directory tree, created in the temp directory and removed afterwards.
3. `autoloading-and-where-a-class-comes-from-3.php` — name resolution: `use`, the global fallback
   that applies to functions and not to classes, and class names held in strings.
