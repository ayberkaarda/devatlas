## Why this exists

`@Transactional` is one word, and it hides three separate decisions: where the
transaction begins, what happens when the method throws, and what happens when a
transactional method calls another one. Get any of them wrong and nothing fails
loudly. The application starts, the tests pass, the endpoint answers 200 — and half
of a two-step operation is committed, or a revocation you wrote is quietly undone by
the exception that was reporting it. In Spring Framework 7.0, which Spring Boot 4.1
builds on, all three defaults are documented and all three surprise people.

## The idea

A transaction is a basket at a self-checkout. You scan things as you go and none of it
is yours until you pay. Walk out without paying and everything goes back on the shelf,
in one motion, as though you had never picked anything up. `Propagation.REQUIRES_NEW`
is handing one item to a friend to buy at the next till: their purchase completes on
its own, whatever happens to yours.

### Where the analogy breaks

A basket is a thing you can see. A transaction is attached to the thread and started
by a proxy wrapped around your bean, so a call that never leaves the object — `this.something()`
— never reaches the proxy and never starts one. There is no empty basket to notice.

The checkout refuses to let you leave with unpaid goods. The default rollback rule is
narrower than "any failure": in proxy mode, any `RuntimeException` or `Error` triggers
rollback, and **any checked `Exception` does not**. A method that declares
`throws SomethingWrong` and throws it commits everything it wrote first.

And a basket's boundary is where you put things in. A transaction's boundary is the
whole method, including everything it calls — a retry loop, a third-party client, an
HTTP request to another service. All of it runs holding a database connection.

## How it works

The defaults: propagation `REQUIRED`, read-write, rollback on unchecked exceptions.
`REQUIRED` means join the caller's transaction if there is one, otherwise start one.
Two `@Transactional` methods calling each other through the container produce one
transaction, one begin, one commit.

```java
@Transactional(readOnly = true)
public LessonDetail getLesson(String slug) { ... }

@Transactional
public void publish(UUID lessonId) { ... }
```

`readOnly = true` is worth writing on reads. It is a hint the resource manager may act
on, and it documents at the call site that this path writes nothing.

`REQUIRES_NEW` suspends the caller's transaction, runs in its own, and commits before
control returns. That is exactly what you need when a record must survive the caller's
failure:

```java
@Service
public class AuditWriter {
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(UUID familyId, String reason) { ... }
}
```

It has to be a separate bean. `@Transactional` is applied by a proxy, and the reference
Spring injects into another bean *is* the proxy; a call to a method on `this` is a
direct call that the proxy never sees, so `REQUIRES_NEW` on such a method is silently
ignored and the work runs inside the caller's transaction after all — where the
caller's rollback erases it. The annotation is still there, looking like a fix.

To roll back on a checked exception, say so:

```java
@Transactional(rollbackFor = LedgerUnavailable.class)
public void archive(UUID id) throws LedgerUnavailable { ... }
```

Method visibility is a related trap with a different shape: since Spring Framework
6.0, `protected` and package-visible methods can be transactional for class-based
proxies, but a transactional method on an interface-based proxy must be `public` and
declared on the proxied interface.

## Common mistakes

**Self-invocation.** `this.doInNewTransaction()` runs in the caller's transaction. The
recorded events show one begin and one rollback where you expected two transactions —
which is how a family revocation gets undone by the exception reporting it.

**Assuming a checked exception rolls back.** It commits. The method still throws, the
caller still sees the failure, and the half-written state is durable.

**A network call inside the boundary.** No exception; just a connection held for the
length of someone else's timeout, and a pool that empties under load.

## Check yourself

<details><summary>A <code>@Transactional</code> method throws a checked exception it declares. What is committed?</summary>
Everything it wrote before throwing. The default rule rolls back for <code>RuntimeException</code> and <code>Error</code> only; <code>rollbackFor</code> changes it.
</details>

<details><summary>Why must a <code>REQUIRES_NEW</code> method live on a different bean from its caller?</summary>
The annotation is applied by a proxy around the bean. A call on <code>this</code> does not pass through the proxy, so no new transaction is started and the setting has no effect.
</details>

<details><summary>Two <code>@Transactional</code> methods, the outer calling the inner through the container, both default. How many transactions?</summary>
One. <code>REQUIRED</code> joins the existing transaction, so there is a single begin and a single commit.
</details>

## Listings

1. `REQUIRED` joining, and `REQUIRES_NEW` suspending, recorded event by event.
2. Unchecked, checked, and `rollbackFor` — three outcomes from one shape.
3. Self-invocation against a call to a separate bean, side by side.
