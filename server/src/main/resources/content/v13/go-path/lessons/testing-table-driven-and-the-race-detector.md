## Why this exists

Testing in Go 1.27 has no assertion library, no annotations and no runner to configure. A test
is a function whose name begins with `Test`, taking a `*testing.T`, in a file whose name ends
in `_test.go`; `go test` compiles the package together with those files and runs them. What
fills the gap where a framework would be is a shape rather than a library: the table-driven
test, in which the cases are data and the checking code is written once. And because the same
toolchain that runs the tests can instrument every memory access, the class of bug that unit
tests are worst at — two goroutines touching one variable — has a tool of its own rather than a
convention.

## The idea

A test table is a checklist on a clipboard, not a paragraph of prose. Each row names the case,
gives its input and states the answer expected. Adding a case is adding a line; the procedure
for checking a line is written once at the bottom.

### Where the analogy breaks

Three ways.

A clipboard is read by one person, top to bottom, and a failure stops them. `t.Run` turns each
row into a subtest with a name of its own, so a failure is attributed to the row and the
remaining rows still run. The names appear in the output — a failing row reports as
`TestPortIsWrong/alt` rather than as a line number in a loop.

A checklist item is independent by convention. Test cases share a process, and anything built
once outside the loop is shared by all of them. The second listing runs the same three cases
against a fixture built once and against a fixture built per case: shared, the second and third
cases fail and the third could never pass; fresh, all three pass, and they still pass when the
order is reversed.

And a clipboard cannot tell whether two inspectors collided. The race detector can, and it does
not need the answer to be wrong to say so. In the run quoted below the program printed the
correct total and the detector still reported two races, which is the point — a data race is
undefined behaviour, not a wrong number, and the wrong number is only how it usually shows up.

## How it works

The table is a slice of anonymous structs; each row gets a name, and `t.Run` uses it:

```go
cases := []struct {
	name     string
	in       string
	wantHost string
	wantPort int
	wantErr  error
}{
	{name: "host and port", in: "example.com:8080", wantHost: "example.com", wantPort: 8080},
	{name: "no colon", in: "example.com", wantErr: ErrNoPort},
}

for _, c := range cases {
	t.Run(c.name, func(t *testing.T) {
		host, port, err := SplitHostPort(c.in)
		if !errors.Is(err, c.wantErr) {
			t.Fatalf("SplitHostPort(%q) error = %v, want %v", c.in, err, c.wantErr)
		}
		...
	})
}
```

`errors.Is` compares correctly against a nil expectation too, so one check covers the success
and failure rows. `t.Errorf` records a failure and continues; `t.Fatalf` stops that subtest.
Run verbosely, the passing suite reports each row by name:

```text
=== RUN   TestSplitHostPort
=== RUN   TestSplitHostPort/host_and_port
=== RUN   TestSplitHostPort/empty_host
=== RUN   TestSplitHostPort/no_colon
PASS
```

and a wrong expectation names the row and the values, not the loop (the elapsed-time lines are
omitted here because they vary):

```text
=== RUN   TestPortIsWrong/alt
    broken_test.go:21: port = 8080, want 8000
FAIL
```

`go test` also runs a subset of `go vet` over the package before running anything, so some
mistakes never reach a test at all. The detector is a build flag rather than a separate tool —
`go test -race`, or `go run -race` for a program — and what it looks for is an access to memory
that no synchronisation orders against another. There are three ways to give it nothing to
find, and the third listing runs all three:

```go
perWorker[i] = i          // storage only this goroutine can reach

mu.Lock()                 // a lock orders the accesses it brackets
guarded++
mu.Unlock()

counter.Add(1)            // sync/atomic, for a single value
```

## Common mistakes

**Sharing a fixture across cases**, which makes the suite depend on the order the rows happen
to be in. No diagnostic; the output of the second listing is the diagnostic.

**Assuming a correct answer means no race.** The detector disagreed, on a run whose total was
exactly right — addresses, goroutine numbers and absolute paths are elided:

```text
WARNING: DATA RACE
Read at ... by goroutine ...:
  main.main.func1()
      shared-counter.go:12 ...
total = 200
Found 2 data race(s)
exit status 66
```

**Writing to one map from several goroutines.** The runtime detects this one without any
instrumentation:

```text
fatal error: concurrent map writes
```

**Calling `t.Fatal` from a goroutine the test started.** It does not stop the test, and `go
vet` says so:

```text
x_test.go:8:3: call to (*testing.T).Fatal from a non-test goroutine
```

**Building a format string before passing it to `Errorf`.** The vet run inside `go test` fails
the build rather than the test:

```text
.\greet_test.go:11:12: non-constant format string in call to (*testing.common).Errorf
.\greet_test.go:11:25: fmt.Sprintf format %d has arg got of wrong type string
FAIL	example/vetintest [build failed]
```

## Check yourself

<details><summary>What does <code>t.Run</code> add that a plain loop over the table does not?</summary>

A named subtest per row. The name appears in the output and can be selected with `-run`, a
failure is attributed to the row rather than to a line inside the loop, and a `t.Fatal` stops
only that row instead of the whole test.

</details>

<details><summary>The race detector reported a race but the program printed the right answer. Is there a bug?</summary>

Yes. A data race is undefined behaviour, and producing the expected value on one run says
nothing about the next one, another machine or another compiler version. The detector reports
the unsynchronised access, which is the defect; the wrong number is only its usual symptom.

</details>

<details><summary>Why build the fixture inside the loop rather than once above it?</summary>

So that no case can observe or disturb what another case did. A fixture built once is shared
state, and shared state makes a case pass or fail according to the order the rows were run in
— which nobody wrote down and which changes when a row is inserted.

</details>

## Listings

1. `testing-table-driven-and-the-race-detector-1.go` — the table and the checking code.
2. `testing-table-driven-and-the-race-detector-2.go` — the shared fixture against the fresh one.
3. `testing-table-driven-and-the-race-detector-3.go` — private slots, a mutex and `sync/atomic`.
