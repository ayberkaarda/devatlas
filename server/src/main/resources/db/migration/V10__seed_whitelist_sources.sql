-- Seeds the whitelist of official sources the blog ingest pipeline is allowed to fetch
-- from (spec: "OTOMATIK BLOG PIPELINE" step 1). A URL that is not one of these rows is
-- never fetched, by any code path -- not by configuration, not by a parameter.
--
-- Each row's verify_url_pattern is a GitHub Releases API endpoint even where the feed
-- itself is not GitHub's: every one of these projects mirrors its tagged releases on
-- GitHub, which is what makes an independent second request possible in the first
-- place (§5.7 of the REST contract) -- the version-confirmation check exists precisely
-- because a feed alone is not independent corroboration of its own claim.
--
-- Identifiers and timestamps are fixed literals, like every other seed migration in this
-- schema, so a developer machine, CI and a fresh deployment all address the same rows.

INSERT INTO whitelist_sources (
    id, name, feed_url, verify_url_pattern, enabled, created_at, updated_at, version
) VALUES
    (
        '019205a0-6000-7000-8000-000000000001',
        'Angular Releases',
        'https://github.com/angular/angular/releases.atom',
        'https://api.github.com/repos/angular/angular/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000002',
        'Spring Boot Releases',
        'https://github.com/spring-projects/spring-boot/releases.atom',
        'https://api.github.com/repos/spring-projects/spring-boot/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000003',
        'OpenJDK Releases',
        'https://github.com/openjdk/jdk/releases.atom',
        'https://api.github.com/repos/openjdk/jdk/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000004',
        'React Releases',
        'https://github.com/facebook/react/releases.atom',
        'https://api.github.com/repos/facebook/react/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000005',
        'Vue Releases',
        'https://github.com/vuejs/core/releases.atom',
        'https://api.github.com/repos/vuejs/core/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000006',
        'Laravel Releases',
        'https://github.com/laravel/framework/releases.atom',
        'https://api.github.com/repos/laravel/framework/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000007',
        'Rails Releases',
        'https://github.com/rails/rails/releases.atom',
        'https://api.github.com/repos/rails/rails/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-6000-7000-8000-000000000008',
        '.NET Releases',
        'https://github.com/dotnet/core/releases.atom',
        'https://api.github.com/repos/dotnet/core/releases/tags/{version}',
        true,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    );
