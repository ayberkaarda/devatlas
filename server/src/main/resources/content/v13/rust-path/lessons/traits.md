## Why this exists

Two types can need the same behaviour without being the same kind of thing. A lesson and a
mind map both have a stable code string; a database error and a network error can both be
rendered for a log. Inheritance answers this by making one type a kind of another, which
works until a type needs behaviour from two places, or until the behaviour has to be added to
a type somebody else wrote. Rust 1.98 has no inheritance at all. A trait is a named set of
method signatures, and any type can be declared to satisfy it in a separate item, written
anywhere the coherence rules allow.

## The idea

A trait is a certification, not a family tree. A food hygiene certificate says what an
establishment is fit to do; the restaurant is not a subclass of the certificate, and the
certificate can be awarded long after the kitchen was built. Two unrelated businesses can
hold the same one, and one business can hold several.

### Where the analogy breaks

A certificate belongs to the thing certified. A trait impl is a separate item, and *who may
write it* is restricted: an impl is only allowed if you own the trait or you own the type.
That is the orphan rule, and it exists so two libraries cannot both certify a third party's
type against a third party's standard and leave the compiler to choose. A certifying body
would simply be trusted; the compiler cannot be, because there would be no way to pick.

An inspector checks a certificate at run time. A trait bound is checked when the program is
compiled, and a generic function is compiled once per concrete type that calls it — there is
no inspection at run time, no lookup, and nothing to pay for. That changes if you ask for it:
`dyn Trait` selects the method through a table carried beside the reference, which costs an
indirect call and is what makes a heterogeneous collection possible.

And certificates are handed out one at a time. A blanket impl awards one to an unbounded
family at once — `to_string` is not implemented by the types that have it, it comes from a
single impl covering everything that implements `Display`. Traits also carry associated types
and required supertraits, so `trait Sink: Send + Sync` is a certificate you cannot hold unless
you already hold two others.

## How it works

A trait declares required methods and may provide default bodies. An impl block supplies the
required ones for one type and may override the defaults. A bound on a generic parameter says
which traits the caller's type must satisfy, and `impl Trait` in argument position is the same
thing with the parameter left unnamed.

```rust
trait Describe {
    fn code(&self) -> &'static str;
    fn describe(&self) -> String { format!("<{}>", self.code()) }   // default body
}

impl Describe for Lesson {
    fn code(&self) -> &'static str { "LESSON" }
    fn describe(&self) -> String { format!("lesson {}", self.slug) }
}

fn report<T: Describe>(item: &T) -> String { item.describe() }      // one copy per T
fn drive(sink: &dyn Sink) { sink.record("queued"); }                // one copy, dispatched
```

Implementing a standard-library trait is how a type joins the language's own machinery.
`Display` gives it `{}` formatting and, through a blanket impl, `to_string`. `From<A> for B`
gives it `.into()` and makes `?` able to convert an `A` into a `B` on the way out of a
function. Not every trait can be used behind `dyn`: a trait with a generic method has no
single function to put in the table, and Rust 1.98 calls that not being dyn compatible.

`impl Trait` in return position captures every lifetime that is in scope, in edition 2024.
That differs from edition 2021, where lifetimes had to be named to be captured, so a signature
copied from older code may need `+ use<>` to say it captures nothing.

## Common mistakes

**Calling a generic function with a type that does not satisfy the bound.** The message names
the type, the trait and the bound that required it:

```text
error[E0277]: `Package` doesn't implement `std::fmt::Display`
10 |     announce(Package { id: String::from("lesson-a") });
   |     -------- ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ unsatisfied trait bound
note: required by a bound in `announce`
 5 | fn announce<T: Display>(item: T) {
   |                ^^^^^^^ required by this bound in `announce`
```

**Implementing somebody else's trait for somebody else's type.** The orphan rule, stated in
full:

```text
error[E0117]: only traits defined in the current crate can be implemented for types defined
outside of the crate
1 | impl std::fmt::Display for Vec<u8> {
  | ^^^^^^^^^^^^^^^^^^^^^^^^^^^-------
  |                            `Vec` is not defined in the current crate
  = note: define and implement a trait or new type instead
```

**Using a trait behind `dyn` that cannot be.** The compiler names the offending method:

```text
error[E0038]: the trait `Sink` is not dyn compatible
4 |     fn record_all<T: AsRef<str>>(&self, lines: &[T]);
  |        ^^^^^^^^^^ ...because method `record_all` has generic type parameters
  = help: consider moving `record_all` to another trait
```

## Check yourself

<details><summary>When does <code>dyn Trait</code> earn its cost over a generic parameter?</summary>

When the concrete types are not known at the call site or vary within one collection — a
`Vec<Box<dyn Shape>>` cannot be written with a generic parameter, because a generic has one
type per instantiation.

</details>

<details><summary>You want a method on a type from another crate. What are the options?</summary>

Define your own trait and implement it for that type, or wrap the type in one of your own.
The orphan rule forbids only the case where both the trait and the type are foreign.

</details>

<details><summary>Where did <code>to_string</code> come from, if nobody implemented it?</summary>

From a blanket impl of `ToString` covering every type that implements `Display`. Implementing
one trait gave the type a second one.

</details>

## Listings

1. `traits-1.rs` — a trait with a default body, two implementors, static dispatch.
2. `traits-2.rs` — supertraits and dynamic dispatch through `dyn`.
3. `traits-3.rs` — implementing `Display` and `From`, and what each unlocks.
