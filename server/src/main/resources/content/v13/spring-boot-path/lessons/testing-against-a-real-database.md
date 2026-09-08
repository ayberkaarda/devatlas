## Why this exists

Most of what a persistence layer does is delegate to the database, so most of what a
test of it can get wrong is delegating to a different one. An in-memory substitute
runs in milliseconds and accepts almost all of your SQL, which is why it is tempting;
it also has its own opinions about types, its own set of constraint behaviours, and
its own isolation semantics. Tests written against it pass, and they are testing the
substitute. Spring Boot 4.1 supports Testcontainers directly, so the honest version
costs a container start and some seconds, and the reason to accept the substitute is
mostly gone.

## The idea

An in-memory database is a flight simulator. It exercises your reflexes, it is cheap,
you can run it a hundred times before lunch, and none of it involves an aircraft.
Running against the real engine is a test flight.

### Where the analogy breaks

Everybody in a simulator knows it is a simulator. An in-memory database advertises SQL
compatibility and behaves exactly like the real one — right up to the check constraint
it does not enforce the same way, the type it widens, the index it treats as advisory.
It does not feel like a simulation until the difference lands somewhere else.

The second break is the useful one: a test flight is expensive and rare, and a
container is neither. That removes the trade the analogy is built on.

The third is where the trouble moves to. A simulator resets instantly to a known
state. A container shared across test classes — which is what Spring's context cache
gives you, and what you want — holds whatever the classes before yours left behind.
The fixture stops being a fact and becomes an assumption.

## How it works

Declare the container as a Spring bean rather than through the JUnit extension.
Spring Boot 4.1's reference documentation is explicit about the difference: container
beans are started before all other beans and stopped after every other bean is
destroyed, and they are retained across test classes that share a cached context,
whereas a container managed by the JUnit extension is stopped when its class finishes
— leaving a cached context full of beans pointing at a database that no longer exists.

```java
@TestConfiguration(proxyBeanMethods = false)
class ContainerConfiguration {
  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
  }
}
```

`@ServiceConnection` supplies the url, user and password to the auto-configured
datasource. That is what lets the test configuration contain no `spring.datasource.*`
block at all — and it should not, because a property there is a property that could
point a test run at a developer's own database and migrate or truncate it.

The tag is pinned to the major version the application is deployed on. An unpinned tag
makes what a green run means depend on the day it was run.

```java
@Import(ContainerConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
abstract class DatabaseBackedTest { }
```

Because the context — and the database behind it — is shared, each class removes the
rows it created and assumes it is not alone. And because migrations run against that
container at startup, the schema under test is the schema that ships, constraints
included.

## Common mistakes

**Asserting on the fixture.** The test builds an object, calls the method, then
asserts on the object it built or on the method's own return value. It passes against
an empty database. It passes with the write commented out. The repair is to re-read
through the same code path a user would and assert on what came back.

**Asserting something that was already true.** `assertThat(found).isNotNull()` after an
update that was supposed to change a status. The row was there before.

**A test that never sees a refusal.** A validation layer never observed refusing
anything is indistinguishable from one that does nothing, so a suite needs the
negative case: the duplicate slug that must fail, the check constraint that must bite.

**Leaving rows behind.** With a shared context, a class that does not clean up makes
the next class's counts depend on test ordering — a failure that appears and
disappears without anyone changing a line.

## Check yourself

<details><summary>Why declare the container as a bean rather than with the JUnit extension?</summary>
Bean lifecycle matches the context's: started before other beans, stopped after them, and kept alive across classes that share a cached context. The extension stops it when its class ends, which can leave a cached context pointing at a stopped container.
</details>

<details><summary>What is wrong with a test that asserts on the value the method under test returned?</summary>
It proves the method built a return value, not that anything was written. It passes even when the statement matched no rows.
</details>

<details><summary>Why should the test configuration contain no <code>spring.datasource.url</code>?</summary>
Because then a test run can only reach the container it created. With one, the suite can reach whatever database is on the host and migrate or truncate it.
</details>

## Listings

1. A throwaway PostgreSQL 16, and two constraints refusing a write.
2. The same call asserted two ways: one green and empty, one that fails.
3. The container as a bean, and the shape a test class inherits.
