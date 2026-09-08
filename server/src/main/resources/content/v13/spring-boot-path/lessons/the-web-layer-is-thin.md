## Why this exists

HTTP is one way into your application. It is rarely the only one. A scheduled job, a
data migration, an administrative command, a second controller added next year — each
reaches the same behaviour by a different route. Every rule written inside a
controller method is a rule that applies to exactly one of those routes. The others
walk past it, and nothing fails while they do; the invariant simply stops holding.
Spring Boot 4.1 gives you enough web machinery that it is easy to put everything in
the controller, so the discipline has to come from you.

## The idea

A controller is a receptionist. It takes the message at the door, checks that the form
is filled in and legible, and walks it to the person who decides. It writes the
outcome on the way out — which desk answered, what status to report — and it never
decides anything about the request itself.

### Where the analogy breaks

A real receptionist makes judgement calls all day, and is right to. A controller must
not, because the building has several entrances and the receptionist stands at one of
them. A rule enforced at reception is not enforced for anyone who came in through the
loading bay.

The second break is subtler: the receptionist's checks are about *you*, while the
controller's checks are about the *shape of the message*. "Is this JSON, does it have
a slug, is the slug under eighty characters" is the controller's business, because it
is a fact about HTTP and about nothing else. "Is this person allowed to enrol" is not,
even though both feel like validation from the doorway.

## How it works

Bind, delegate, choose a status. That is the whole method.

```java
@PostMapping(path = "/api/v1/enrolments", consumes = APPLICATION_JSON_VALUE)
ResponseEntity<EnrolResponse> enrol(@Valid @RequestBody EnrolRequest request) {
  return ResponseEntity.status(201).body(service.enrol(request.trackSlug()));
}
```

`@Valid` on the argument runs Bean Validation before the method body executes. A
request that fails it never reaches the service at all — the handler method is not
invoked, and Spring MVC raises `MethodArgumentNotValidException`, which the default
handling turns into a 400.

What comes back is a response record, not an entity. An entity is the shape of your
table; publishing it makes every column rename a breaking API change and every lazy
association a query fired after the transaction closed.

```java
record EnrolResponse(String trackSlug, int seatsLeft) {}
```

Some HTTP decisions genuinely belong here, and it is worth naming them so the rule
does not become "controllers do nothing". Status codes, `Content-Type`, `Content-Language`,
`Vary`, `Location`, `ETag`, cache directives, pagination parameters — these are facts
about the protocol. A controller that sets `Vary: Accept-Language` because a shared
cache must not serve one caller's negotiated locale to another is doing its own job.

```java
return ResponseEntity.ok()
    .header(CONTENT_LANGUAGE, body.locale())
    .header(VARY, ACCEPT_LANGUAGE)
    .body(body);
```

The service, meanwhile, takes plain arguments and returns plain values. It has no
`HttpServletRequest`, no `ResponseEntity`, no knowledge that HTTP exists — which is
what makes it callable from the job, the migration and the test.

## Common mistakes

**The rule in the controller.** Everything works until a second caller appears. The
diagnostic is not an exception; it is a row in the database that the rule says cannot
exist. A test that calls the service directly and expects the refusal fails
immediately and is the cheapest way to find it.

**Returning the entity.** Spring Boot 4.1 registers an interceptor that keeps the
persistence context open for the whole request, which is what `spring.jpa.open-in-view`
controls; with it set to `false` — as it should be, so that queries are not fired
during response writing — a lazy association reached at serialisation time throws
`LazyInitializationException`, after the status line has often already been sent.

**Injecting the repository into the controller.** The controller then owns a
transaction boundary it cannot express, and every read runs in its own.

## Check yourself

<details><summary>Which of these belongs in a controller: the 201 status, the seat limit, the JSON content type, the audit record?</summary>
The status and the content type. The seat limit and the audit record are behaviour, and belong wherever every caller reaches them.
</details>

<details><summary>A request fails <code>@Valid</code>. Was the service called?</summary>
No. Validation of a <code>@RequestBody</code> argument happens before the handler method is invoked, so the body never runs.
</details>

<details><summary>Why is returning an entity from a controller a problem even when it serialises correctly today?</summary>
It ties the wire format to the table. A column rename becomes a breaking API change, and a lazy association becomes a query — or a failure — at serialisation time.
</details>

## Listings

1. A thin controller, and the same rule reached without HTTP at all.
2. The rule written into the controller, and the caller that walks past it.
3. What the boundary rejects before the service is ever called.
