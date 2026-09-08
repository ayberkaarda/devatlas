## Why this exists

In a language with declared implementation, a type can only be used through an abstraction its
author anticipated. If a library ships `Widget` without saying `implements Serializable`, no
amount of code on your side makes a `Widget` serializable; you wrap it. That coupling runs the
wrong way — the concrete type has to know about every abstraction, and abstractions therefore
pile up at the bottom of a dependency graph where nobody can see what they are for. Go 1.27
inverts it. An interface type is a set of method signatures, and a type satisfies it by having
those methods. The satisfaction is checked at the point of assignment, so an interface can be
declared today for types that were written years ago, in packages that will never import it.

## The idea

An interface is a job advertisement, not a club membership. The consumer posts what it needs
done — "wanted, anything that can `Write([]byte) (int, error)`" — and any type that can do the
work qualifies. It never applies, and it never hears that the ad exists.

### Where the analogy breaks

Three ways.

An advertisement is read by a person who can be persuaded. Satisfaction is decided by the
compiler at every assignment, with no negotiation, and the message names the missing method
exactly: `Square does not implement Shape (missing method Perimeter)`. Being nearly qualified
is being unqualified.

An applicant is a person; here the receiver form decides who applies. The method set of `T`
holds its value-receiver methods, and the method set of `*T` holds those plus its
pointer-receiver methods, so a type with one pointer-receiver method qualifies only as a
pointer. That rule has a lesson of its own later in this track.

And an employer ends up with a person. An interface value ends up with a *pair*: the dynamic
type of what it holds and the value itself. That pair can hold a type and a nil value, which is
why an interface variable holding a nil `*Rect` is not equal to nil. The second listing prints
both cases together — `nil interface: value=<nil> type=<nil> isNil=true` beside `typed nil:
type=*main.Rect isNil=false`.

## How it works

Declare the interface where it is used, keep it small, and assign a concrete value to it. The
concrete type mentions nothing:

```go
type Notifier interface {
	Notify(message string) string
}

type EmailChannel struct{ Address string }

func (c EmailChannel) Notify(message string) string { return "email to " + c.Address }

var _ Notifier = EmailChannel{}   // a compile-time assertion, if you want one
```

Interfaces embed: satisfying a combination means having every method of every embedded
interface. Going the other way — asking what an interface value actually holds — is a type
assertion, and the two-result form asks without risking a panic:

```go
if square, ok := s.(Square); ok {
	use(square.Diagonal())
}

switch v := s.(type) {           // a type switch, when there are several cases
case Square:
	report(v.Side)
default:
	report(v.Area())
}
```

An assertion may also name another interface, which asks whether the dynamic type has that
interface's methods. That is how `io.Copy` discovers a `WriteTo` on the value it was handed.

## Common mistakes

**Assuming a type qualifies because it has most of the methods.**

```text
.\missing-method.go:15:16: cannot use Square{…} (value of struct type Square) as Shape value in variable declaration: Square does not implement Shape (missing method Perimeter)
```

**Asserting to a type that could never be in there.** The compiler can prove it and refuses
to build:

```text
.\impossible-assertion.go:15:14: impossible type assertion: s.(Label)
	Label does not implement Shape (missing method Area)
```

**Using the one-result assertion on a value that might be something else.** This one is a run
time panic, and the message names both types:

```text
panic: interface conversion: main.Shape is main.Square, not main.Rect
```

**Comparing an interface to nil after storing a typed nil pointer in it.** No diagnostic at
all; the comparison is simply false, for the reason above.

## Check yourself

<details><summary>Where should an interface be declared — beside the consumer or beside the implementations?</summary>

Beside the consumer. It describes what that code needs, so it stays small and it does not force
the implementations to import anything. Several unrelated packages can satisfy it without
knowing about each other or about it.

</details>

<details><summary>What is the difference between a type switch and a chain of two-result assertions?</summary>

None in capability; the type switch binds the value at the matched type in each case and gives
a `default` branch, so it reads as one decision instead of several. A chain of assertions is
what you write when only one type is interesting.

</details>

<details><summary>An interface value prints as <code>&lt;nil&gt;</code> but <code>err != nil</code> is true. What is in it?</summary>

A dynamic type and a nil value of that type. `%v` printed the value half; the comparison looks
at both halves, and an interface is nil only when it holds no type at all.

</details>

## Listings

1. `interfaces-are-satisfied-not-declared-1.go` — nothing declares that it implements anything.
2. `interfaces-are-satisfied-not-declared-2.go` — assertions, type switches, and the nil pair.
3. `interfaces-are-satisfied-not-declared-3.go` — one type satisfying three standard interfaces.
