## Why this exists

Authorisation written per endpoint fails in one direction only: the endpoint somebody
forgot. A new controller method, a path added to an existing one, a route that moved —
each is open until somebody remembers to close it, and nothing reports the omission
because an open endpoint answers 200. The arrangement Spring Security 7.1 supports,
and that Spring Boot 4.1 wires up for you, inverts that: the chain ends in a rule that
matches everything, so a path nobody has thought about is refused rather than served.
The forgetting still happens; it just fails safe.

## The idea

Picture a corridor with a row of desks. Every visitor walks past all of them in order.
One desk reads your badge and writes down who you are, another stamps the time, one
notes what to do if something goes wrong further along — and the desk at the far end,
the last one, is the only one that decides whether you may go through the door. It
decides on what the desks before it wrote down.

### Where the analogy breaks

In a corridor you can see that a desk is missing. The filter chain is assembled from
configuration and the order is not written anywhere you look; the only way to see it
is to print it, which is worth doing at least once.

A corridor also has one route. Inside `authorizeHttpRequests` the rules are consulted
in the order written and **only the first match is applied**. A broad rule written
first makes every narrower rule below it unreachable — the request is authorised
against the broad one and the specific one is never consulted. There is no warning,
because "unreachable" is not distinguishable from "deliberately shadowed".

And a human at a desk treats "no badge" differently from "wrong badge". Spring
Security answers with whatever `AuthenticationEntryPoint` is configured, and with no
authentication mechanism configured at all the default answers **403** to an anonymous
caller, not 401. If your clients branch on 401 to refresh a token, you have to say so.

## How it works

One `SecurityFilterChain` bean, and the last rule is the one that matters.

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
  return http
      .csrf(csrf -> csrf.disable())
      .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
      .authorizeHttpRequests(auth -> auth
          .requestMatchers(HttpMethod.GET, "/api/v1/tracks/**").permitAll()
          .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
          .anyRequest().authenticated())
      .build();
}
```

Narrow first, broad last, `anyRequest()` at the end. Denying by default turns the set
of rules into an allow list, which is the only form that can be reviewed. Writing a
matcher after `anyRequest()` is refused outright with `IllegalStateException: Can't
configure requestMatchers after anyRequest` — one ordering mistake the framework does
catch.

Disabling CSRF here is a decision, not a default worth copying blindly: it holds
because authenticated calls carry a bearer token in a header, which a cross-site form
cannot set, and because there is no server-side session to fix or steal.

Custom filters are placed relative to the existing ones, and placement is a
correctness question rather than a style one:

```java
.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
.addFilterAfter(perUserRateLimitFilter, JwtAuthenticationFilter.class)
```

A filter that keys a budget on the caller's identity has to run after the filter that
establishes it; placed before, every request looks anonymous and the budget is keyed
on nothing.

One trap worth knowing: any `Filter` that is a Spring bean is also registered with the
embedded servlet container, which runs a second copy outside the security chain,
ahead of authentication. A filter meant only for the chain is constructed inline
rather than declared as a bean.

## Common mistakes

**A broad rule before a narrow one.** Anonymous callers get 403 on a path you meant to
publish. The rules read correctly top to bottom; only the order is wrong.

**`permitAll()` without a method.** `requestMatchers("/api/v1/tracks/**")` matches
`POST` and `DELETE` as well as `GET`. Naming the method is one argument.

**Expecting 401.** With no authentication mechanism configured, an anonymous caller
gets 403 from the default entry point. Configure one that answers 401 if that is the
contract you published.

## Check yourself

<details><summary>Two rules match a request. Which applies?</summary>
The first one written. Matching stops at the first match, so a broad rule placed above a narrow one makes the narrow one unreachable.
</details>

<details><summary>Why must a rate-limit filter keyed on the user run after the authentication filter?</summary>
Because the principal it keys on does not exist until authentication has run. Earlier in the chain, every request is anonymous and the budget has nothing to key on.
</details>

<details><summary>Why does declaring a security filter as a <code>@Bean</code> risk running it twice?</summary>
Any <code>Filter</code> bean is registered with the embedded servlet container as well as being placed in the chain, so a second copy runs outside the chain and ahead of authentication.
</details>

## Listings

1. Three paths, three callers, and what a default-deny chain answers.
2. Rule order: the shadowed rule, the correct order, and the refused build.
3. The chain printed in order, with a custom filter on each side of authentication.
