-- Extends the whitelist of official sources the blog ingest pipeline may fetch from, so
-- that the languages, runtimes, frameworks and everyday tools a working developer is
-- likely to use are all represented. The first seed covered eight projects; a reader whose
-- stack was Python, Node.js, Go, Rust, Django, Next.js or Kubernetes found nothing here.
--
-- Selection: the languages and web frameworks that lead the yearly developer surveys and
-- package-registry download counts, the runtimes they ship on, the two dominant mobile
-- toolkits, and the handful of tools nearly every stack touches (Git, Docker, Kubernetes,
-- Terraform, PostgreSQL, Gradle, LLVM for the C and C++ toolchain). The list stops there
-- on purpose: every row is a feed the scheduler pulls every six hours and a stream of
-- items a human has to review, so a project earns a row by being widely used, not by
-- existing.
--
-- Every feed and every verify URL below was fetched before it was written down, and each
-- verify pattern was exercised with a version string taken from its own feed, because
-- the pattern is filled with whatever the feed's item titles yield ("v26.8.1" keeps its
-- "v", "Rust 1.98.1" yields "1.98.1", "Announcing Swift 6.3.3" yields "6.3.3") and a
-- pattern built for the wrong shape fails silently on every item, forever.
--
-- Verify patterns come in four shapes, chosen per project by how it tags and announces:
--   * .../releases/tags/<tag>         the project publishes GitHub Releases (the shape
--                                     the first seed uses).
--   * .../git/ref/tags/<tag>          the project only pushes tags; GitHub renders them
--                                     in the releases feed, but the Releases API answers
--                                     404 for them, while the git ref endpoint answers
--                                     for any tag with a body that names it.
--   * .../git/matching-refs/tags/v<v> the project announces a minor line ("TypeScript
--                                     6.0", "Next.js 16.3") while its tags carry a patch
--                                     component. A prefix match confirms the line
--                                     exists; an unknown prefix returns an empty array,
--                                     whose body cannot contain the version string, so
--                                     it still fails.
--   * a page on the project's own site where GitHub carries no usable tag for the
--                                     announced string: Go's per-release notes page,
--                                     PostgreSQL's per-release notes page, and Ruby's
--                                     release listing (Ruby tags read "v3_3_12" while the
--                                     feed says "3.3.12"; the listing is the official
--                                     record that the release exists, and the query
--                                     string, which the server ignores, only carries the
--                                     string into the URL). These are cross-domain, so
--                                     they are an even more independent second request
--                                     than GitHub is.
--
-- Feeds are the project's GitHub releases feed wherever that feed is usable. Five are not:
-- Go's tag titles read "[release-branch.go1.27] go1.27.1", from which the extractor would
-- take "1.27"; Kotlin's and Swift's GitHub feeds are flooded with nightly builds and
-- development snapshots that would either be rejected daily or, worse, verified as the
-- release they snapshot; Next.js publishes several canary releases a day, each a real
-- tag that would pass verification and reach the review queue; and PostgreSQL's mirror
-- tags read "REL_18_6", which carries no version the extractor recognises. Those five use
-- the project's own announcement feed instead (Go blog, Kotlin releases category, Swift
-- forum announcements, Next.js blog, postgresql.org versions feed). swift.org's own Atom
-- feed was considered and rejected: its CDN serves it gzip-encoded regardless of the
-- request, and the pipeline's HTTP client does not decompress.
--
-- Identifiers and timestamps are fixed literals, continuing the first seed's series, so a
-- developer machine, CI and a fresh deployment all address the same rows.

INSERT INTO whitelist_sources (
    id, name, feed_url, verify_url_pattern, enabled, created_at, updated_at, version
) VALUES
    -- Languages and runtimes
    (
        '019205a0-6000-7000-8000-000000000009',
        'Python Releases',
        'https://github.com/python/cpython/releases.atom',
        'https://api.github.com/repos/python/cpython/git/ref/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000010',
        'Node.js Releases',
        'https://github.com/nodejs/node/releases.atom',
        'https://api.github.com/repos/nodejs/node/releases/tags/v{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000011',
        'TypeScript Releases',
        'https://github.com/microsoft/TypeScript/releases.atom',
        'https://api.github.com/repos/microsoft/TypeScript/git/matching-refs/tags/v{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000012',
        'Go Releases',
        'https://go.dev/blog/feed.atom',
        'https://go.dev/doc/go{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000013',
        'Rust Releases',
        'https://github.com/rust-lang/rust/releases.atom',
        'https://api.github.com/repos/rust-lang/rust/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000014',
        'Kotlin Releases',
        'https://blog.jetbrains.com/kotlin/category/releases/feed/',
        'https://api.github.com/repos/JetBrains/kotlin/releases/tags/v{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000015',
        'Swift Releases',
        'https://forums.swift.org/c/general-announce/24.rss',
        'https://api.github.com/repos/swiftlang/swift/releases/tags/swift-{version}-RELEASE',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000016',
        'PHP Releases',
        'https://github.com/php/php-src/releases.atom',
        'https://api.github.com/repos/php/php-src/releases/tags/php-{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000017',
        'Ruby Releases',
        'https://github.com/ruby/ruby/releases.atom',
        'https://www.ruby-lang.org/en/downloads/releases/?release={version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000018',
        'Deno Releases',
        'https://github.com/denoland/deno/releases.atom',
        'https://api.github.com/repos/denoland/deno/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000019',
        'Bun Releases',
        'https://github.com/oven-sh/bun/releases.atom',
        'https://api.github.com/repos/oven-sh/bun/releases/tags/bun-{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000020',
        'LLVM Releases',
        'https://github.com/llvm/llvm-project/releases.atom',
        'https://api.github.com/repos/llvm/llvm-project/releases/tags/llvmorg-{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    -- Web frontend
    (
        '019205a0-6000-7000-8000-000000000021',
        'Next.js Releases',
        'https://nextjs.org/feed.xml',
        'https://api.github.com/repos/vercel/next.js/git/matching-refs/tags/v{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000022',
        'Nuxt Releases',
        'https://github.com/nuxt/nuxt/releases.atom',
        'https://api.github.com/repos/nuxt/nuxt/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000023',
        'Svelte Releases',
        'https://github.com/sveltejs/svelte/releases.atom',
        'https://api.github.com/repos/sveltejs/svelte/releases/tags/svelte@{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000024',
        'Vite Releases',
        'https://github.com/vitejs/vite/releases.atom',
        'https://api.github.com/repos/vitejs/vite/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000025',
        'Tailwind CSS Releases',
        'https://github.com/tailwindlabs/tailwindcss/releases.atom',
        'https://api.github.com/repos/tailwindlabs/tailwindcss/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000026',
        'Bootstrap Releases',
        'https://github.com/twbs/bootstrap/releases.atom',
        'https://api.github.com/repos/twbs/bootstrap/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    -- Web backend
    (
        '019205a0-6000-7000-8000-000000000027',
        'Express Releases',
        'https://github.com/expressjs/express/releases.atom',
        'https://api.github.com/repos/expressjs/express/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000028',
        'NestJS Releases',
        'https://github.com/nestjs/nest/releases.atom',
        'https://api.github.com/repos/nestjs/nest/git/ref/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000029',
        'Fastify Releases',
        'https://github.com/fastify/fastify/releases.atom',
        'https://api.github.com/repos/fastify/fastify/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000030',
        'Django Releases',
        'https://github.com/django/django/releases.atom',
        'https://api.github.com/repos/django/django/git/ref/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000031',
        'Flask Releases',
        'https://github.com/pallets/flask/releases.atom',
        'https://api.github.com/repos/pallets/flask/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000032',
        'FastAPI Releases',
        'https://github.com/fastapi/fastapi/releases.atom',
        'https://api.github.com/repos/fastapi/fastapi/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000033',
        'Symfony Releases',
        'https://github.com/symfony/symfony/releases.atom',
        'https://api.github.com/repos/symfony/symfony/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000034',
        'Quarkus Releases',
        'https://github.com/quarkusio/quarkus/releases.atom',
        'https://api.github.com/repos/quarkusio/quarkus/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000035',
        'Gin Releases',
        'https://github.com/gin-gonic/gin/releases.atom',
        'https://api.github.com/repos/gin-gonic/gin/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000036',
        'Spring Framework Releases',
        'https://github.com/spring-projects/spring-framework/releases.atom',
        'https://api.github.com/repos/spring-projects/spring-framework/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    -- Mobile
    (
        '019205a0-6000-7000-8000-000000000037',
        'Flutter Releases',
        'https://github.com/flutter/flutter/releases.atom',
        'https://api.github.com/repos/flutter/flutter/git/ref/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000038',
        'React Native Releases',
        'https://github.com/facebook/react-native/releases.atom',
        'https://api.github.com/repos/facebook/react-native/releases/tags/v{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    -- Tools and infrastructure
    (
        '019205a0-6000-7000-8000-000000000039',
        'Git Releases',
        'https://github.com/git/git/releases.atom',
        'https://api.github.com/repos/git/git/git/ref/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000040',
        'Docker CLI Releases',
        'https://github.com/docker/cli/releases.atom',
        'https://api.github.com/repos/docker/cli/git/ref/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000041',
        'Kubernetes Releases',
        'https://github.com/kubernetes/kubernetes/releases.atom',
        'https://api.github.com/repos/kubernetes/kubernetes/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000042',
        'Terraform Releases',
        'https://github.com/hashicorp/terraform/releases.atom',
        'https://api.github.com/repos/hashicorp/terraform/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000043',
        'PostgreSQL Releases',
        'https://www.postgresql.org/versions.rss',
        'https://www.postgresql.org/docs/release/{version}/',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000044',
        'Gradle Releases',
        'https://github.com/gradle/gradle/releases.atom',
        'https://api.github.com/repos/gradle/gradle/releases/tags/v{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    );
