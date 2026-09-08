## Why this exists

A language with exceptions gives a function two exits: the one written in its signature and an
invisible one that unwinds the stack. Reading a call tells you nothing about the second, and
the compiler will not tell you either. Go 1.27 has one exit. The specification declares a
single predeclared interface — `type error interface { Error() string }` — and a function that
can fail returns one alongside its result. Failure is therefore data: it can be stored in a
variable, put in a struct, compared, wrapped with context, collected into a group and passed
around. The cost is that every call site that can fail says so in three lines instead of none,
and that visible verbosity is the feature being bought.

## The idea

An error is a delivery note, not an alarm. The parcel and the note arrive together at the same
door, and it is the receiver who decides what the note means. Nothing rings, nothing empties
the building, and the driver has already left.

### Where the analogy breaks

A delivery note is one flat document. An error is a chain. `fmt.Errorf` with `%w` produces an
error that *contains* the one below it, so `errors.Is` can walk down looking for a particular
sentinel value and `errors.As` can walk down looking for a particular concrete type. Formatting
with `%v` instead produces the same human-readable text and no chain at all — the second
listing prints `errors.Is through %v: false` beside the identical message, which is the whole
difference in one line.

A note can be binned with no consequence. Ignoring an error is worse: it is legal, silent and
invisible in review. Go refuses to compile an unused *variable*, but a returned error assigned
to `_`, or a call whose results are simply dropped, draws nothing from the compiler.

And a note is either present or absent. An error interface value can be present and empty at
the same time. A function that declares a concrete pointer type as its result and returns a nil
one produces an interface carrying the type with a nil value, and `err != nil` is then true
when nothing went wrong. The third listing prints exactly that: `checkTrap on a good key
returned err != nil: true`.

## How it works

Errors are made with `errors.New` for a fixed message or `fmt.Errorf` for one with detail. A
sentinel is a package-level variable so that callers can name the condition instead of matching
on prose; two errors made from the same text are never equal, which is what makes the variable
necessary:

```go
var ErrNotFound = errors.New("account not found")

func withdraw(id string, amount int) (int, error) {
	acct, ok := ledger[id]
	if !ok {
		return 0, ErrNotFound
	}
	return acct.balance - amount, nil
}
```

Context is added by wrapping. `%w` keeps the original reachable; a type may also join the chain
by implementing `Unwrap`. Inspection is then a question about the whole chain rather than the
top of it:

```go
return fmt.Errorf("load %s: %w", name, err)   // errors.Is finds what err was

var fieldErr *FieldError
if errors.As(err, &fieldErr) {                // the target is a pointer to the type
	log(fieldErr.Field, fieldErr.Line)
}
```

`errors.Join` returns one error carrying several, and returns nil when every argument is nil —
which is what lets a validator collect every problem and still have one `if err != nil` at the
call site.

## Common mistakes

**Passing something that is not an error to `%w`.** `go run` accepts it; `go vet` does not:

```text
wrap-nonerror.go:6:34: fmt.Errorf format %w has arg "disk full" of wrong type string
```

and the program prints the damage rather than failing:

```text
load config: %!w(string=disk full)
```

**Calling `fmt.Errorf` with arguments and no verbs.** The arguments vanish:

```text
errorf-noverb.go:6:37: fmt.Errorf call has arguments but no formatting directives
```

**Giving `errors.As` a value instead of a pointer to one.** This one panics at run time:

```text
panic: errors: target must be a non-nil pointer
```

**Wrapping with `%v` and then testing with `errors.Is`.** No diagnostic; the check just returns
false for ever, because `%v` copied the text and dropped the chain.

## Check yourself

<details><summary>Why is a sentinel declared as a variable rather than written at each call site?</summary>

`errors.New` returns a distinct value every time, so two errors with identical text are not
equal. Callers compare against the one value the package exported, which is what `errors.Is`
looks for as it walks the chain.

</details>

<details><summary>When does <code>errors.Join</code> return nil?</summary>

When every argument is nil, including the case of no arguments at all. That is what lets a
validator build a slice of problems unconditionally and still return a clean nil when there
were none.

</details>

<details><summary>A function returns <code>*MyError</code> declared as <code>error</code>. Why is <code>err != nil</code> true after a success?</summary>

The interface value holds a type and a value. Returning a nil `*MyError` fills in the type and
leaves the value nil, and an interface is nil only when both halves are. Return a literal `nil`
on the success path instead of a typed nil variable.

</details>

## Listings

1. `errors-are-values-1.go` — sentinels, `errors.Is`, and errors as ordinary values.
2. `errors-are-values-2.go` — wrapping, `errors.As`, and what `%v` throws away.
3. `errors-are-values-3.go` — `errors.Join`, and the interface holding a typed nil.
