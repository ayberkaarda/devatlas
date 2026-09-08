## Why this exists

Three clients read this API: a web build, a desktop build, a download engine. Each of
them has to decide what to do when a call fails — retry, sign the user out, show a
translated message, stop and report. They can only do that if failures have a shape.
Left alone, they do not: a validation failure comes back in Spring's default form, a
domain failure in whatever the controller author invented that afternoon, and an
unhandled bug in a framework body carrying the exception's own message. Three shapes,
three branches, and the third one leaking a hostname. Spring Boot 4.1 will not choose
a contract for you; it gives you one place to declare one.

## The idea

Think of the label on a returned parcel. Whatever went wrong, wherever it went wrong,
the label has the same fields in the same places: a reason code, a short description.
You can sort a pallet of returns by reason without opening a single box.

### Where the analogy breaks

A parcel's label is written by the depot that rejected it, and every depot could
invent its own layout — which is precisely the failure this lesson is about. The
contract only holds because one place writes the label.

A shipping label also carries the sender's address, which is useful on a parcel and
catastrophic in an error body. `connection to db-primary.internal:5432 refused for user
bytelore_app` is a genuine error message and a map of your estate; it belongs in the
log, correlated by request id, and never in a response.

And a label is read by a person who can interpret an unusual note. Yours is read by a
`switch` statement. That forces the reason to come from a closed, documented set — a
code a client can branch on — rather than from free text somebody will reword.

## How it works

One `@RestControllerAdvice`, and it is the only place in the application that builds
an error body.

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorBody> handleApi(ApiException e) {
    return ResponseEntity.status(e.code().status())
        .body(new ErrorBody(e.code().name(), e.getMessage(), List.of()));
  }
  // one handler per exception family, and nothing outside this class
}
```

The code carries its own status, so no handler decides `404` versus `409` by hand and
no two paths disagree about which failure is which.

Validation is translated rather than forwarded. Bean Validation constraint names are
mapped onto a closed set of per-field codes, and property paths are converted to the
casing the wire uses, so `codeExamples[1].language` reaches the client as
`code_examples[1].language`:

```java
new FieldProblem(
    toSnakeCasePath(error.getField()),
    CONSTRAINT_CODES.getOrDefault(error.getCode(), "FORMAT"));
```

Anything unlisted degrades to a general code rather than inventing one no client has
been told about.

Last, the catch-all. Without it, an unmapped exception leaves as a framework default
body — a different shape, sometimes carrying the exception's message.

```java
@ExceptionHandler(Exception.class)
ResponseEntity<ErrorBody> handleUnexpected(Exception e) {
  log.error("Unhandled exception", e);
  return ResponseEntity.status(500)
      .body(new ErrorBody("INTERNAL_ERROR", "An internal error occurred.", List.of()));
}
```

Spring Framework 6.0 added support for RFC 9457 problem details, and Spring Boot 4.1
exposes it as `spring.mvc.problemdetails.enabled`. That is a perfectly good contract
to adopt. What is not an option is having none: either take that shape or define your
own, but the clients need one answer to "what does a failure look like".

## Common mistakes

**`@ExceptionHandler` on individual controllers.** Each one is correct in isolation.
Together they produce different bodies for the same exception, and the difference is
invisible until a client hits the second endpoint.

**No handler for `Exception`.** The one path you did not anticipate is the one that
leaks. In a standalone MVC setup the exception escapes the dispatch entirely; in a
running application it becomes the default error body.

**Returning the exception's message.** It reads as helpful. It is written for you, not
for a caller, and it names hosts, users, table columns and sometimes request values.

## Check yourself

<details><summary>Why does the error body carry a code as well as a message?</summary>
The message is prose for whoever reads a log. The code is what a client branches on and maps to its own translated text, so the server can reword a message without breaking anyone.
</details>

<details><summary>What is wrong with mapping every unlisted Bean Validation constraint to a new code named after it?</summary>
The set stops being closed. Clients cannot branch on codes they have never been told about, and adding a constraint silently changes the API.
</details>

<details><summary>An endpoint throws an exception nobody wrote a handler for. What should the caller receive?</summary>
The same body shape as every other failure, with a generic code and no detail. The detail belongs in the log.
</details>

## Listings

1. One advice, four outcomes, one body shape.
2. Per-controller handling: the same exception, two shapes, and one leak.
3. Field errors translated into closed codes and snake_case paths.
