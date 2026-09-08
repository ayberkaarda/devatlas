# Content Corpus Plan — v13

The seventeen tracks of the first corpus, their modules and their lessons.
`content-corpus.md` defines the format this fills in; ADR-0007 records why the
corpus is loaded the way it is.

**This file is the scope.** An author writes the lessons named here and does not
add, merge, rename or reorder them. Choosing what a platform teaches is a
product decision and it is made once, here, rather than seventeen times by
seventeen different hands.

Every track is three modules of three lessons, in the shape
**Foundations → the model that is particular to this thing → real work**.
Difficulty runs `BEGINNER`, `INTERMEDIATE`, `ADVANCED` by module.

Publication order is the order below. It begins with the technologies this
application is itself built from, because that is where the reviewer's own
knowledge is deepest and where the review protocol gets its first honest
calibration. C++ is last, alone, for the reasons in its own section.

---

## 01 · `angular-path` — The Angular Path
Teaches **Angular 22**. Rewritten in place; its existing identifiers are kept
(`content-corpus.md` §4).

**Foundations**
1. *What a component actually is* — a class, a template, and the boundary between them.
2. *Signals, and why the framework stopped guessing* — state that announces its own changes.
3. *Reading state without asking for it* — `computed`, and why derived state is not a copy.

**The reactive model**
4. *Effects are for the outside world* — what belongs in an effect and what does not.
5. *Inputs as signals* — data arriving from a parent, and the `undefined` a route hands you.
6. *Change detection when nothing is zone-driven* — what actually triggers a re-render.

**Building something real**
7. *Routing, lazily* — a route that loads its own code.
8. *Talking to a server* — `HttpClient`, interceptors, and where a failure belongs.
9. *Testing a component without a browser* — `TestBed`, and asserting structure over text.

---

## 02 · `spring-boot-path` — The Spring Boot Path
Teaches **Spring Boot 4.1**.

**Foundations**
1. *A bean, and who makes it* — the container, and why construction injection wins.
2. *Configuration that is typed* — properties into records, validated at startup.
3. *The web layer is thin* — a controller that decides nothing.

**Persistence and transactions**
4. *An entity is not a table* — mapping, and where the mismatch bites.
5. *What a transaction actually spans* — propagation, and the rollback that surprised you.
6. *Queries you can defend* — derived queries, JPQL, and when to write SQL.

**Building something real**
7. *An error contract* — one exception handler, one body shape, no leaked internals.
8. *Security that says no by default* — the filter chain, and why order matters.
9. *Testing against a real database* — Testcontainers, and the fixture that lies.

---

## 03 · `typescript-path` — The TypeScript Path
Teaches **TypeScript 5.9**.

**Foundations**
1. *What a type really is* — structural typing, and why the shape is the contract.
2. *Inference is not guessing* — where the compiler knows more than you wrote.
3. *`any`, `unknown`, and the difference that matters* — the escape hatch and the honest one.

**The type system's own model**
4. *Narrowing* — how control flow changes what a value is.
5. *Unions, and the exhaustive check* — the `never` branch that fails the build.
6. *Generics are parameters* — writing a function that keeps the caller's type.

**Building something real**
7. *Modelling data that arrives from elsewhere* — parsing at the boundary, not asserting.
8. *`strict` and what each flag buys* — the settings that catch real bugs.
9. *Declaration files* — describing code you did not write.

---

## 04 · `java-path` — The Java Path
Teaches **Java 21 LTS**.

**Foundations**
1. *Objects, references, and the thing people call a pointer* — what a variable holds.
2. *Records* — data that says it is data, and what you get for free.
3. *Immutability by default* — `final`, defensive copies, and why it is cheaper than it looks.

**The language's own model**
4. *Interfaces and the sealed hierarchy* — closing a type so the compiler can help.
5. *Pattern matching* — `switch` over shapes rather than values.
6. *Generics and erasure* — what survives to runtime and what does not.

**Building something real**
7. *Collections, and choosing the right one* — cost, ordering, and the map you reached for.
8. *Streams without cleverness* — when a loop is clearer.
9. *Exceptions as a contract* — checked, unchecked, and what a caller can do about it.

---

## 05 · `postgresql-path` — The PostgreSQL Path
Teaches **PostgreSQL 16**.

**Foundations**
1. *A table is a promise* — types, constraints, and why the database should refuse.
2. *Keys* — primary, foreign, and the natural key that betrayed you.
3. *`NULL` is not a value* — three-valued logic and the comparisons it breaks.

**How the database thinks**
4. *Indexes, and what they cost* — what a lookup does with one and without.
5. *Reading a query plan* — `EXPLAIN`, and the sequential scan that was fine.
6. *Transactions and isolation* — what another session can see, and when.

**Building something real**
7. *Joins that mean what you meant* — inner, outer, and the row multiplication nobody wanted.
8. *Aggregation and window functions* — a running total without a loop.
9. *Migrations* — changing a schema that is already holding data.

---

## 06 · `python-path` — The Python Path
Teaches **Python 3.13**.

**Foundations**
1. *Names, objects, and rebinding* — what assignment actually does.
2. *Mutable default arguments and other shared state* — the bug everyone writes once.
3. *Iterables, iterators, generators* — laziness, and where the memory goes.

**The language's own model**
4. *Everything is an object, including the class* — attributes, and the lookup order.
5. *Duck typing with a safety net* — type hints, and what they do and do not check.
6. *Context managers* — `with`, and cleanup that happens even when it goes wrong.

**Building something real**
7. *Data classes and the shape of your data* — `dataclass`, and when a dict stops being enough.
8. *Errors that say something* — exception hierarchies and the bare `except` that hid a bug.
9. *Testing, and the fixture that made your test pass for the wrong reason.*

---

## 07 · `django-path` — The Django Path
Teaches **Django 6.1**.

**Foundations**
1. *A model is the schema* — fields, migrations, and one source of truth.
2. *The request, the view, the response* — the path a request actually takes.
3. *Templates render, they do not decide* — where logic belongs.

**The ORM's own model**
4. *QuerySets are lazy* — when the query runs, and how to see it.
5. *The N+1 problem* — `select_related`, `prefetch_related`, and measuring first.
6. *Migrations that are safe to run twice* — and what happens on a table with rows.

**Building something real**
7. *Forms and validation* — refusing bad input in one place.
8. *Authentication and permissions* — who the user is and what that lets them do.
9. *Testing a Django application* — the test client, and the database it gets.

---

## 08 · `rust-path` — The Rust Path
Teaches **Rust 1.98, edition 2024**.

**Foundations**
1. *Ownership* — one owner, and what "move" means for your data.
2. *Borrowing* — references, and the rule that prevents the bug.
3. *Lifetimes are descriptions, not instructions* — what the annotation tells the compiler.

**The language's own model**
4. *`Result` and `Option`* — errors and absence as values you must handle.
5. *Traits* — shared behaviour without inheritance.
6. *Pattern matching and exhaustiveness* — the compiler enumerating your cases.

**Building something real**
7. *Collections and iterators* — chaining adaptors, and what the chain does and does not cost.
   (This line previously claimed an iterator chain "compiles to the same thing" as a
   loop. The author measured it with `rustc -O --emit=asm` on Rust 1.98 and the two
   functions produced different machine code — the chain was unrolled four ways with
   different register allocation. The defensible claim is that there is no per-stage
   cost, not that the emitted code is identical; the lesson says that instead.)
8. *Error handling across a program* — propagation, conversion, and a type callers can use.
9. *Testing, and what `cargo test` actually runs.*

---

## 09 · `nodejs-path` — The Node.js Path
Teaches **Node.js 22 LTS**.

**Foundations**
1. *One thread, and why that is not a limitation* — the event loop, concretely.
2. *Callbacks, promises, `async`/`await`* — the same thing, three notations.
3. *Modules* — ESM, CommonJS, and the interop that trips everyone.

**The runtime's own model**
4. *Streams* — processing data you have not finished receiving.
5. *The file system and paths that survive Windows* — and the async you forgot to await.
6. *Blocking the loop* — how to notice, and what to do instead.

**Building something real**
7. *An HTTP server without a framework* — what a framework is doing for you.
8. *Environment, configuration and secrets* — what belongs in the process and what does not.
9. *Testing, and the async assertion that passed because nobody waited.*

---

## 10 · `react-path` — The React Path
Teaches **React 19**.

**Foundations**
1. *A component is a function of its props* — rendering as a description.
2. *State, and why you do not mutate it* — the update that did not re-render.
3. *Lists and keys* — identity across renders, and the index that lied.

**The rendering model**
4. *When a component re-renders* — and the three reasons it did.
5. *Effects are an escape hatch* — synchronising with something outside React.
6. *Derived state is a smell* — computing during render instead of storing.

**Building something real**
7. *Lifting state, and when to stop* — composition over a bigger store.
8. *Fetching data* — loading, error and the race you did not cancel.
9. *Testing behaviour, not implementation* — queries that survive a refactor.

---

## 11 · `vue-path` — The Vue Path
Teaches **Vue 3.5**.

**Foundations**
1. *The template is a contract* — declarative rendering and the data behind it.
2. *`ref` and `reactive`* — two ways to hold state, and when each breaks.
3. *Computed properties* — derived values that cache themselves.

**The reactivity model**
4. *How reactivity actually tracks* — what it can see and what it cannot.
5. *Watchers, and when a computed would have done* — side effects on change.
6. *Component communication* — props down, events up, and the shortcut that hurt.

**Building something real**
7. *The composition API in practice* — extracting logic that two components share.
8. *Routing and lazily loaded views.*
9. *Testing a component* — mounting, and asserting what a user would see.

---

## 12 · `go-path` — The Go Path
Teaches **Go 1.27**.

**Foundations**
1. *Types, zero values, and the absence of surprises* — what a declaration gives you.
2. *Slices are not arrays* — the header, the backing array, and the aliasing bug.
3. *Errors are values* — returning them, wrapping them, and never ignoring them.

**The language's own model**
4. *Interfaces are satisfied, not declared* — implicit implementation.
5. *Goroutines* — cheap concurrency and the one you leaked.
6. *Channels and `select`* — communicating instead of sharing.

**Building something real**
7. *Structs, methods and the pointer receiver question.*
8. *The standard library is the framework* — an HTTP server in what you already have.
9. *Testing, table-driven* — and the race detector.

---

## 13 · `csharp-path` — The C# Path
Teaches **C# 14 on .NET 10**.

(This line said C# 13. The author asked the compiler rather than inferring the
language version from the SDK number: a file containing `#error version` built
under SDK 10.0.400 answers `Language version: 14.0`. Teaching 13 would also have
cost this track its best material, since the `field` keyword that lesson 4 is
built around is a C# 14 feature.)

**Foundations**
1. *Value types and reference types* — where the copy happens.
2. *Nullable reference types* — the compiler arguing about `null`, usefully.
3. *Records and `with`* — data with value semantics.

**The language's own model**
4. *Properties, and why they are not fields* — encapsulation that costs nothing.
5. *LINQ* — deferred execution and the query that ran twice.
6. *`async`/`await` and the context it captures.*

**Building something real**
7. *Collections and the interface to accept* — `IEnumerable`, and what to return.
8. *Dependency injection in the host* — lifetimes, and the captured dependency bug.
9. *Testing* — and the async void that swallowed a failure.

---

## 14 · `kotlin-path` — The Kotlin Path
Teaches **Kotlin 2.4** on the JVM.

**Foundations**
1. *Null safety as a type* — `?`, and the compiler's guarantee.
2. *`val`, `var`, and immutability that is not deep.*
3. *Data classes* — equality, copying, and destructuring.

**The language's own model**
4. *Extension functions* — adding behaviour without inheritance, and where they resolve.
5. *Scope functions* — `let`, `apply`, `run`, and choosing rather than guessing.
6. *Sealed classes and exhaustive `when`.*

**Building something real**
7. *Collections, sequences, and the lazy chain.*
8. *Coroutines* — suspension, structured concurrency, and the scope you cancelled.
9. *Interoperating with Java* — platform types and the null that got through.

---

## 15 · `php-path` — The PHP Path
Teaches **PHP 8.2**.

**Foundations**
1. *Types in a language that did not have them* — declarations, `strict_types`, and coercion.
2. *Arrays are two things at once* — list and map, and the surprise when it matters.
3. *Comparison* — `==`, `===`, and the bug the loose one wrote.

**The language's own model**
4. *Classes, interfaces and traits* — composition in a single-inheritance language.
5. *Enums* — a fixed set the type system enforces.
6. *Exceptions and errors* — what is catchable and what ends the request.

**Building something real**
7. *Composer and autoloading* — where a class comes from.
8. *Handling a request safely* — input, escaping, and prepared statements.
9. *Testing* — and the global state that made a test order-dependent.

---

## 16 · `ruby-path` — The Ruby Path
Teaches **Ruby 3.4**.

**Foundations**
1. *Everything is an object, including `nil`* — and what that buys you.
2. *Blocks* — the argument that is a piece of code.
3. *Truthiness, and the two things that are false.*

**The language's own model**
4. *Modules and mixins* — sharing behaviour, and the lookup chain.
5. *Method missing and the price of magic* — metaprogramming, and when not to.
6. *Symbols and strings* — two ways to name a thing.

**Building something real**
7. *Enumerable* — the interface behind most of what you write.
8. *Exceptions, `ensure` and cleanup you can rely on.*
9. *Testing* — and the mock that tested itself.

---

## 17 · `cpp-path` — The Modern C++ Path
Teaches **C++23**, compiled with `clang++`. Published last and alone.

This track is held to a stricter standard than the others, and the reason is
stated in the lessons themselves: in C++ a program can compile cleanly, run,
produce the expected answer, and still be wrong. Every listing here is compiled
with warnings as errors **and** run under the address and undefined-behaviour
sanitizers. Raw `new`/`delete`, C arrays and manual lifetime management appear
only in lesson 3, which is about why they are not used.

**Foundations**
1. *Values, references, and what a copy costs* — and where the compiler elides it.
2. *`const` means two different things* — and why the distinction is load-bearing.
3. *Lifetime, and the dangling reference* — the bug the sanitizer catches and the reader cannot.

**The language's own model**
4. *RAII* — ownership expressed as a destructor.
5. *Smart pointers* — `unique_ptr`, `shared_ptr`, and the cycle that never freed.
6. *Move semantics* — transferring rather than copying, and the moved-from state.

**Building something real**
7. *The standard containers, and the iterator you invalidated.*
8. *Templates and concepts* — generic code that fails at the definition, not the call.
9. *Undefined behaviour, catalogued* — signed overflow, uninitialised reads, aliasing, and how each is detected.
