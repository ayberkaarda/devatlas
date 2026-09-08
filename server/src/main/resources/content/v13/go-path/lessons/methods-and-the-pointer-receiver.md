## Why this exists

A struct in Go 1.27 is a value. Assigning one copies every field, passing one to a function
copies it again, and comparing two with `==` compares them field by field. A method is an
ordinary function with one extra parameter written before the name, and that parameter — the
receiver — is subject to the same rule as any other. Declare it as `T` and the method works on
a copy; declare it as `*T` and it works on the caller's struct. Nothing at the call site
distinguishes the two, because `c.Add()` is written identically either way, so the decision is
made once at the declaration and everybody lives with it. Getting it wrong produces the
quietest bug in the language: a method that appears to do its job and changes nothing.

## The idea

Choosing a receiver is choosing what to hand the clerk: a photocopy of the file, or the file
itself. Give them a photocopy and they can scribble on it all day; the cabinet is untouched.
Give them the file and their corrections are the record.

### Where the analogy breaks

In three places.

A clerk knows which one they were given. Your reader does not — the call looks the same, and
the compiler quietly takes the address of an addressable value on your behalf, so `value.Add(1)`
is shorthand for `(&value).Add(1)`. The only place the answer is written down is the method
declaration.

A photocopy can always be made. An address cannot always be taken. A map element has no
address, so a pointer-receiver call on a map entry does not compile; the repair is to read the
value out, change it and write it back, or to store pointers in the map instead.

And a photocopy is harmless. A struct copy is not, if the struct contains a `sync.Mutex`: the
copy carries its own lock, which guards nothing that the original's lock guards. `go vet` finds
this one, which is the strongest argument for running it.

## How it works

The method set of `T` contains its value-receiver methods. The method set of `*T` contains
those *and* its pointer-receiver methods. Interface satisfaction is decided against method
sets, so mixing receivers on one type means only the pointer satisfies an interface that
mentions any pointer-receiver method:

```go
type Tally struct{ n int }

func (t Tally) Total() int  { return t.n }   // in the method set of Tally and *Tally
func (t *Tally) Add(by int) { t.n += by }    // in the method set of *Tally only

var _ Resettable = (*Tally)(nil)             // holds; the value type would not
```

A pointer receiver may be called on a nil pointer as long as the method does not dereference
it, which is a real technique rather than a curiosity — it lets the zero value of a pointer
type answer questions:

```go
func (n *node) Len() int {
	if n == nil {
		return 0
	}
	return 1 + n.Next.Len()
}
```

Embedding is composition with promotion, not inheritance. An embedded type's fields and methods
are reachable through the outer type, and the outer type may declare a method of the same name
to shadow one — but a method *written on the embedded type* still calls the embedded type's own
methods. The third listing shows exactly that: `Report` shadows `Label`, and the promoted
`Describe` prints `describe -> base-7`, not the report's label. To vary behaviour, hold an
interface in a field instead.

## Common mistakes

**Choosing a value receiver for a method that mutates.** No diagnostic anywhere; the program
simply does nothing:

```text
expected 5, got 0
```

**Expecting the value type to satisfy an interface when a method has a pointer receiver.**

```text
.\pointer-method-on-value.go:12:16: cannot use Tally{} (value of struct type Tally) as Adder value in variable declaration: Tally does not implement Adder (method Add has pointer receiver)
```

**Calling a pointer-receiver method on a map entry.**

```text
.\pointer-method-on-map-entry.go:11:14: cannot call pointer method Add on Tally
```

**Assigning to a field of a struct stored in a map**, which is the same rule seen from the
other side:

```text
.\assign-to-map-field.go:9:2: cannot assign to struct field byName["a"].N in map
```

**Copying a struct that contains a mutex.** `go vet` reports both the method and the
assignment:

```text
registry-copied.go:13:9: Count passes lock by value: command-line-arguments.Registry contains sync.Mutex
registry-copied.go:17:11: assignment copies lock value to other: command-line-arguments.Registry contains sync.Mutex
```

## Check yourself

<details><summary>A type has one pointer-receiver method. Which receiver should its other methods use?</summary>

The pointer, for consistency. Mixing receivers on one type is legal and is the source of the
confusion above: the value satisfies some interfaces and not others, and readers have to check
each method to know which. Pick one form per type.

</details>

<details><summary>Why does <code>value.Add(1)</code> compile when <code>Add</code> has a pointer receiver?</summary>

Because `value` is addressable, so the compiler rewrites the call as `(&value).Add(1)`. A map
element or a function's return value is not addressable, and there the same call is refused.

</details>

<details><summary>A promoted method calls another method by name. Whose does it call?</summary>

The one belonging to the type the method was written on. There is no dynamic dispatch back into
the outer type, so shadowing a method does not change what a promoted method does. Composition
through an interface field is how that is arranged deliberately.

</details>

## Listings

1. `methods-and-the-pointer-receiver-1.go` — value against pointer receiver, and a nil receiver.
2. `methods-and-the-pointer-receiver-2.go` — method sets, interfaces and addressability.
3. `methods-and-the-pointer-receiver-3.go` — embedding promotes; it does not override.
