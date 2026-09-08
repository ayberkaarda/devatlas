## Why this exists

A PHP request normally begins with a fresh process state and ends a few milliseconds later, so
global state has historically cost very little: whatever a script scribbles on `$_GET`, on a static
property or on an ini setting is thrown away at the end of the request. A test suite is the one
place that assumption stops holding. Every case runs in the *same* process, one after another, and
anything one case leaves behind is waiting for the next.

The symptom is unmistakable once you have seen it: a test that passes on its own and fails in the
suite, or passes in the suite and fails when a colleague adds an unrelated case above it. The cause
is almost never the failing test. It is something the previous one did not put back.

## The idea

A shared test process is a workbench in a shared workshop. Each job is supposed to leave the bench
as it found it. A job that leaves a clamp tightened does not fail — it finishes perfectly — and the
next person's work comes out crooked for reasons that have nothing to do with what they did.

### Where the analogy breaks

A tightened clamp is visible. The state that breaks a PHP suite is not on the bench at all: it is a
`private static` property inside a class nobody in the failing test mentions, an entry in `$_GET`,
or a process-wide ini value. Nothing in the failing test's source refers to it, which is why
reading the failing test is the wrong place to start looking.

The workshop analogy also implies a fixed order of jobs. Order is the variable here, not the
constant. The same two cases pass in one sequence and fail in the other, so the honest way to
demonstrate the problem is to run both sequences and compare, rather than to run one and record
what came out.

And a clamp can be loosened by hand. Some of this state cannot: a class with a `private static`
and no reset method is genuinely unresettable from outside, which is the point at which the design
has to change rather than the test.

## How it works

A static property lives for the whole process. Three lines of a lazily-initialised singleton are
enough:

```php
final class Config
{
    private static ?array $values = null;
    public static function load(array $values): void { self::$values ??= $values; }
    public static function get(string $key): string { return self::$values[$key] ?? '(unset)'; }
}
```

`??=` is the bug. The second `load()` is silently ignored, so the first test to run decides what
every later test sees, and the failure moves when the order does:

```
currency then locale
  currency                   ok
  locale                     FAIL got EUR
locale then currency
  locale                     ok
  currency                   FAIL got GBP
```

Neither test is wrong. Superglobals behave the same way, and so do ini settings; a case that sets
`$_GET['admin']` leaves it set, and the next case sees a request it never made.

The repair is to stop the state being global. Passing the configuration and the clock in as
constructor arguments makes each case build its own, and there is nothing left to leak:

```php
final class Receipt
{
    public function __construct(private readonly Config $config, private readonly Clock $clock) {}
}
```

The clock matters as much as the configuration. A method that reads the current time makes the
expected value depend on the day the suite runs, which is the same class of defect wearing
different clothes. A `Clock` interface with a fixed implementation removes it.

Order independence can then be asserted rather than hoped for: run the same cases in several
sequences and compare the outcomes, printing the property — *every order agrees* — rather than one
sequence's output.

```
bool(true)
```

One note on `assert()`. It is a language construct, not a function, and when `zend.assertions` is
set to `-1` the engine does not compile it at all. That makes it unsuitable as the assertion in a
test suite, because the suite's behaviour would depend on an ini setting; the harness in these
listings throws instead.

## Common mistakes

**Resetting state in the test that needs it clean.** That fixes one order and not the other. The
reset belongs before *every* case, or the state belongs in a constructor argument.

**Calling a static factory from inside the code under test.** It reaches past every seam the test
has, and no argument the test passes can influence it.

**Reading the clock, the filesystem or a random source inside a unit under test.** Each one makes
the expected value a function of when and where the suite ran.

**Trusting a green suite that has only ever run in one order.** Order independence is a claim like
any other, and it stays unverified until something runs the cases in a different sequence.

## Check yourself

<details><summary>A test passes alone and fails in the suite. Where do you look first?</summary>

At what ran before it, not at the test itself. Then for shared state: `static` properties,
superglobals, ini settings, and anything registered on a global handler.

</details>

<details><summary>Why is injecting a <code>Clock</code> better than resetting a static clock between tests?</summary>

A reset has to be remembered in every suite that ever touches the class, and a new test written a
year later will not know about it. An argument cannot be forgotten: the constructor will not run
without it.

</details>

<details><summary>Why not use <code>assert()</code> as the assertion in a test harness?</summary>

Because `zend.assertions` decides whether it is compiled at all. A suite whose checks disappear
under a production ini setting is not a suite.

</details>

## Listings

1. `testing-and-the-static-that-outlived-the-test-1.php` — the smallest harness that reports
   usefully, with one case failing on purpose.
2. `testing-and-the-static-that-outlived-the-test-2.php` — the same cases in two orders, over a
   static property, a superglobal and an ini setting.
3. `testing-and-the-static-that-outlived-the-test-3.php` — the state passed in as arguments, a
   fixed clock, and order independence asserted rather than assumed.
