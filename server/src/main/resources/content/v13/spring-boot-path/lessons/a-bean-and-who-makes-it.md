## Why this exists

A service needs a repository. A controller needs the service. Written by hand, every
one of those relationships becomes a `new` call somewhere, and the somewhere is
usually the class that needs the collaborator — which means the class also decides
which implementation it gets, how it is configured, and how long it lives. Replace
the repository in a test and you cannot: the decision was compiled in. Spring Boot
4.1 moves that decision out of the class and into a container that reads what each
object asks for and supplies it. What you get back is not magic; it is an ordinary
object that somebody else assembled.

## The idea

Think of a workshop foreman with a stack of parts slips. Each worker writes down
what they need before they can start — a lathe, a length of steel, the drawing — and
the foreman reads every slip, works out an order in which everyone can be supplied,
and hands each worker their parts before the shift opens. No worker walks to the
store themselves, and no worker begins half-equipped.

### Where the analogy breaks

Three ways, and the third is the one that bites.

A foreman knows what the parts are *for*. The container matches on type alone: two
beans of the same type and it stops, because "which one" is not a question it can
answer from a type. A foreman can be asked for a part mid-shift; the container works
the whole plan out when the application starts, and by the time your code runs the
graph is already built.

And a foreman hands each worker their own screw. The container, by default, hands
every worker **the same one**. A bean is a singleton: one instance shared by every
caller, for the life of the application. A field you mutate in a bean is mutated for
everybody, on every thread. That is not a flaw in the container — it is the reason a
bean should hold its collaborators and nothing else.

## How it works

A class declares its collaborators as constructor parameters. With exactly one
constructor, nothing else is needed: no annotation, no lookup, no framework type.

```java
@Service
public class LessonService {
  private final LessonRepository repository;

  public LessonService(LessonRepository repository) {
    this.repository = repository;
  }
}
```

`final` is doing real work. The object is complete when the constructor returns, so
there is no state in which it exists but is not usable, and no later caller can swap
the repository out. Compare the alternative:

```java
@Service
public class LessonService {
  @Autowired private LessonRepository repository; // never null... in the container
}
```

That field is `null` between construction and injection, it cannot be `final`, and
`new LessonService()` in a test compiles and then fails on the first call.

The cost of constructor injection is that a cycle becomes unbuildable, and the
container says so at startup with `BeanCurrentlyInCreationException`. That is the
feature: two classes that each need the other are one class wearing two names.

```java
@Bean @Primary Clock buildClock() { ... }
@Bean Clock releaseClock() { ... }
@Bean Report report(@Qualifier("releaseClock") Clock clock) { ... }
```

When two candidates genuinely both belong, `@Primary` names the default and
`@Qualifier` overrides it at the point of use.

## Common mistakes

**Field injection to break a cycle.** It works in a bare `AnnotationConfigApplicationContext`
— both objects are created empty and filled afterwards. It does not work in a Spring
Boot 4.1 application: `spring.main.allow-circular-references` defaults to `false`
there, so the same `BeanCurrentlyInCreationException` is thrown either way.

**Two beans of one type.** `NoUniqueBeanDefinitionException` at startup, naming both
candidates. Adding a second implementation without deciding which is the default is
the usual way in.

**Mutable state on a singleton.** No exception, no diagnostic, just wrong answers
under concurrency. This one has no compiler behind it.

## Check yourself

<details><summary>Why can a constructor-injected field be <code>final</code> when an <code>@Autowired</code> field cannot?</summary>
A <code>final</code> field must be assigned by the end of the constructor. Constructor injection supplies the value as an argument, so it can be. Field injection assigns it by reflection after the object already exists, which a <code>final</code> field forbids.
</details>

<details><summary>A bean holds a <code>private int counter</code> that a request handler increments. What is wrong?</summary>
The bean is a singleton, so every request shares that field on every thread. The increments race, and nothing reports it.
</details>

<details><summary>Two beans implement one interface and a constructor asks for the interface. What happens, and when?</summary>
<code>NoUniqueBeanDefinitionException</code>, at startup rather than on the first request, because singleton beans are pre-instantiated when the context is created.
</details>

## Listings

1. A container, two ordinary objects, and construction order made visible.
2. A constructor cycle refused, and the same cycle admitted through fields.
3. Two candidates of one type: the refusal, then `@Primary` and `@Qualifier`.
