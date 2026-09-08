## Why this exists

Configuration arrives as text. A YAML file, an environment variable, a command-line
argument — all of it is strings, all of it comes from outside the build, and none of
it has been checked by anything. Read with `@Value("${bytelore.auth.access-ttl}")`
scattered across a dozen classes, the same key gets read in three places, spelled
slightly differently in one of them, parsed into a `String` in another, and the
mistake surfaces at 03:00 on the first request that needs it. Spring Boot 4.1 offers
a different arrangement: declare a type, bind the whole group to it once, and check
it before the application finishes starting.

## The idea

A configuration properties type is passport control at the border of your
application. Everything crossing is inspected once, against a declared shape — this
must be an integer, this a `Duration`, this must not be blank — and stamped. Past the
desk, no code asks again whether the value is real, because nothing that was not real
got through.

### Where the analogy breaks

Passport control inspects everyone who crosses. The binder only inspects the keys some
type has *declared*. A key nobody declared — a typo, a property from a copied file, a
setting for a feature that was removed — is not turned away at the border. It is
simply never read, and nothing anywhere reports it. The failure mode is not a refusal,
it is a default silently winning.

The second break: a passport is checked on the way in and again on the way out.
Binding happens once, at startup. The record in memory is the configuration for the
life of the process; changing the file changes nothing until a restart.

## How it works

A record has exactly one constructor, so constructor binding applies with no extra
annotation. Every component is final.

```java
@ConfigurationProperties("bytelore.download")
@Validated
public record DownloadProperties(
    @Min(1) int maxAttempts,
    @DefaultValue("PT5S") Duration retryDelay,
    @NotBlank String userAgent) {}
```

The type has to be registered before anything binds it. `@EnableConfigurationProperties(DownloadProperties.class)`
on a configuration class, or `@ConfigurationPropertiesScan` on the application class.
Registration is what does the work, not the annotation on the type: a bean created by
an ordinary `@Bean` method or by component scanning is built by the regular Spring
mechanisms, and constructor binding is not available to those — Spring Boot 4.1's
reference documentation says so explicitly. That is why a properties type that must be
a `@Component` is written the old way, as a class with setters.

Keys bind by relaxed rules, so `bytelore.download.max-attempts`, `BYTELORE_DOWNLOAD_MAXATTEMPTS`
and `maxAttempts` all reach the same component.

```yaml
bytelore:
  download:
    max-attempts: 5
    retry-delay: PT2S
```

`@Validated` is what turns a bad value into a startup failure. Spring Boot validates a
`@ConfigurationProperties` type when it carries that annotation and a JSR-303
implementation is on the classpath; the failure is a `BindValidationException` naming
every field and constraint at once, before any request is served.

```java
@Bean
JwtService jwtService(AuthProperties properties) {
  if (properties.jwtSecret().getBytes(UTF_8).length < 32) {
    throw new IllegalStateException("bytelore.auth.jwt-secret must be at least 32 bytes.");
  }
  ...
}
```

Some rules are not expressible as an annotation — a key measured in bytes rather than
characters, a value that must be consistent with another. Those go in the constructor
of the bean that consumes them, and they still fail at startup, which is the property
that matters.

## Common mistakes

**A misspelled key.** `retry-dely` in the file, `retryDelay` in the record. The record
takes its default, the application starts clean, and the setting you thought you
changed has no effect. There is no diagnostic; the only defence is a default you can
live with, or no default at all so the value is required.

**Nested properties left unvalidated.** Constraints on a nested type are not applied
unless the field holding it is annotated `@Valid`. The outer object passes, the inner
one was never checked.

**A default signing key.** A `@DefaultValue` on a secret means every deployment that
forgot to set it shares one value — and so does anyone who reads the source.

## Check yourself

<details><summary>A record annotated <code>@ConfigurationProperties</code> is also annotated <code>@Component</code>. Is it bound?</summary>
Not by constructor binding. Component scanning creates the bean through the regular mechanisms, and constructor binding is unavailable there; a type registered that way has to expose setters instead.
</details>

<details><summary>Where does a bad value stop the application, and where does a misspelled key stop it?</summary>
A bad value stops it at startup, when <code>@Validated</code> is present. A misspelled key stops it nowhere: the key is never read.
</details>

<details><summary>Why bind a group of related keys to one type rather than reading each with <code>@Value</code>?</summary>
One place spells the keys, one place converts them, one place validates them, and one object can be handed to a test. Scattered <code>@Value</code> reads have none of those properties.
</details>

## Listings

1. A record bound from properties: relaxed keys, a `Duration`, a list, a default.
2. `@Validated` refusing two bad values at startup, by field and constraint.
3. The same type registered two ways, and the one that is never bound.
