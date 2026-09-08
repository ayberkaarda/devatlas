## Why this exists

Ruby 3.4 ships Minitest with the interpreter, so a test needs no project setup: require it,
subclass `Minitest::Test`, write methods beginning with `test_`. That part takes ten minutes.
The part that takes longer is learning which tests are worth having. A test that replaces the
collaborator with a double, tells the double what to answer, and then asserts that the answer
came back is a closed loop: it passes on correct code, it passes on broken code, and it will
go on passing after the collaborator it describes has been deleted. This lesson is mostly
about that loop, because a suite full of it is worse than no suite — it reports confidence it
has not earned.

## The idea

A test double is a stand-in actor at a rehearsal. The lead is unavailable, so somebody reads
the lines to let the scene be rehearsed. That is useful when the lead is slow to arrive, or
when the scene needs a cue the lead cannot reliably give — an error, a timeout, a date.

### Where the analogy breaks

A stand-in reads the script. A mock reads whatever you tell it to say, including lines the
real actor would never deliver. If you write both the stand-in's line and the assertion that
the line was heard, the rehearsal proves that you can write two matching sentences. Listing
2 makes this concrete: `Checkout#total` passes its arguments to the collaborator in the wrong
order, and a mock told only what to return passes the test anyway.

The second break is that a stand-in notices when the script changes and the real actor
doesn't exist any more. A mock does not. It answers `discount` cheerfully after the real class
has been refactored to `discount_for`, so the test stays green while describing a method
nobody has. Nothing in Minitest checks a double against the class it is standing in for.

## How it works

A test class subclasses `Minitest::Test`. `setup` runs before each test method,
`assert_equal` compares, and `assert_raises` returns the exception so its message can be
checked too.

```ruby
class BasketTest < Minitest::Test
  def setup = @basket = Basket.new

  def test_rejects_a_non_positive_quantity
    error = assert_raises(ArgumentError) { @basket.add("apple", 0) }
    assert_equal "qty must be positive", error.message
  end
end
```

`require "minitest/autorun"` is the ordinary way to run such a file. The listings here run
each test explicitly instead and print the result, because Minitest's own summary reports a
duration and a random seed, and neither is the same twice.

`Minitest::Mock` has two halves and both matter. `expect` says what call is anticipated —
optionally with the arguments, matched by value, by class, or by a block that decides — and
what to answer. `verify` asserts the anticipated calls actually happened. Skip `verify` and an
unmet expectation costs nothing at all; the mock simply never complains.

The distinction that decides whether a mock test is worth anything is whether the expectation
describes the *call* or only the *answer*. `pricing.expect(:discounted, 900)` with a block
that accepts anything asserts nothing about how the collaborator was used.
`pricing.expect(:discounted, 900, [1000, 10])` states the arguments, and listing 2 records it
catching the reversed pair.

`Object#stub` is the smaller tool and usually the better one: it replaces a single method on a
real object for the duration of a block, leaving the rest of the object real, and restores it
afterwards.

## Common mistakes

**Asserting the value the mock was told to return.** No diagnostic — the test passes. Listing
2 records `test_with_a_mock_that_answers_the_question` passing against code with a genuine
argument-order bug, while the version stating the arguments fails:

```text
MockExpectationError: mocked method :discounted called with unexpected arguments [10, 1000]
```

and the real collaborator fails without any expectation at all:

```text
Expected: 900 | Actual: -90
```

**Forgetting `verify`.** The expectation is checked only when you ask:

```text
expectation set, never called -> no error yet
verify                        -> MockExpectationError: Expected save() => true
```

**Expecting a mock to reject an unknown message helpfully.** It raises a `NoMethodError`
naming the mock's expectations, which reads oddly the first time:

```text
NoMethodError: unmocked method :anything_at_all, expected one of []
```

**Letting a stub outlive the test.** `stub` restores the method when its block ends; replacing
a method by assignment does not, and the next test inherits it.

**Mocking something you own and can build.** A `Basket` or a `Pricing` is cheap to construct,
and a real one catches interface drift that no double can.

## Check yourself

<details><summary>What makes a mock-based test circular?</summary>

Asserting the value the mock was configured to return, without constraining the call. The
assertion then tests the test's own setup; the code under test could pass its arguments in any
order, or ignore them.

</details>

<details><summary>What does <code>verify</code> add, and what happens without it?</summary>

It asserts that every expectation set with `expect` was actually met, raising
`MockExpectationError` if not. Without it an expectation that never happened is silently
ignored.

</details>

<details><summary>Why is <code>stub</code> usually safer than a mock?</summary>

It changes one method on an otherwise real object, for the duration of a block, and restores
it afterwards. The rest of the object's behaviour — and any drift in its interface — is still
exercised.

</details>

## Listings

1. `testing-and-the-mock-that-tested-itself-1.rb` — a `Minitest::Test` class run explicitly,
   with `setup`, `assert_raises`, and what a failing assertion reports.
2. `testing-and-the-mock-that-tested-itself-2.rb` — the same bug seen by three tests: a mock
   that answers, a mock that states the call, and the real collaborator.
3. `testing-and-the-mock-that-tested-itself-3.rb` — `verify`, argument matching by value,
   class and block, `stub`, and a double that outlived the interface it described.
