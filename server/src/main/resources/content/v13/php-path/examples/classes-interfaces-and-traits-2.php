<?php

declare(strict_types=1);

// A trait is copied into the using class at compile time. It is not a parent,
// it has no type, and two traits that offer the same name collide.

trait Timestamps
{
    private ?string $touchedBy = null;

    public function touch(string $who): void
    {
        $this->touchedBy = $who;
    }

    public function touchedBy(): ?string
    {
        return $this->touchedBy;
    }
}

trait Counts
{
    // Trait constants were added in PHP 8.2.
    public const STEP = 5;

    // Each using class gets its own copy of a static property.
    private static int $seen = 0;

    public static function seen(): int
    {
        return self::$seen;
    }

    public static function record(): int
    {
        return self::$seen += self::STEP;
    }
}

final class Article
{
    use Timestamps;
    use Counts;
}

final class Comment
{
    use Counts;
}

Article::record();
Article::record();
Comment::record();
printf("Article::seen=%d Comment::seen=%d STEP=%d\n", Article::seen(), Comment::seen(), Article::STEP);

$a = new Article();
$a->touch('ana');
var_dump($a->touchedBy());

// A trait is not a type. There is no `instanceof Timestamps`.
var_dump(trait_exists(Timestamps::class), interface_exists(Timestamps::class));
var_dump(in_array(Timestamps::class, class_uses(Article::class), true));

// Two traits offering the same name is a compile-time fatal unless the class
// resolves it. insteadof picks a winner; `as` gives the loser another name.
trait Loud
{
    public function speak(): string
    {
        return 'LOUD';
    }
}

trait Quiet
{
    public function speak(): string
    {
        return 'quiet';
    }
}

final class Announcer
{
    use Loud, Quiet {
        Loud::speak insteadof Quiet;
        Quiet::speak as whisper;
    }
}

$announcer = new Announcer();
echo $announcer->speak(), ' / ', $announcer->whisper(), "\n";

// Precedence: the class body wins over a trait, and a trait wins over a parent.
class Base
{
    public function origin(): string
    {
        return 'parent';
    }
}

trait Origin
{
    public function origin(): string
    {
        return 'trait';
    }
}

class FromTrait extends Base
{
    use Origin;
}

class FromClass extends Base
{
    use Origin;

    public function origin(): string
    {
        return 'class';
    }
}

echo (new FromTrait())->origin(), ' ', (new FromClass())->origin(), ' ', (new Base())->origin(), "\n";
