-- Seed content: one published track, two modules, four lessons and one mind map.
-- Enough shape for the read endpoints, the manifest and the desktop client to be
-- exercised against something real before any authoring UI exists.
--
-- Identifiers are fixed UUIDv7 literals rather than generated values, so that a
-- developer machine, CI and a fresh deployment all address the same rows. The
-- version nibble is 7 and the variant bits are 10xx, matching every identifier
-- the application mints at runtime.
--
-- Timestamps are fixed literals for the same reason: re-running the migration on
-- another machine must not produce different data.
--
-- Markdown bodies are written as escaped strings (E'...\n...') rather than as
-- literal multi-line text. The line endings of this file therefore cannot leak
-- into the stored content: a checkout that produced CRLF here would otherwise
-- store CRLF bodies, and the digest computed over them would differ from the one
-- computed on a machine that checked the file out with LF -- the exact failure
-- the hashing rules exist to prevent.
--
-- The package columns (sha256, package_bytes, package_size_bytes) are left null.
-- Packaging is the job of the serializer that produces the canonical bytes, and
-- a row with no stored package is a row no manifest may advertise. Seeding a
-- digest by hand here would put a second, unverifiable implementation of the
-- hashing rules into a migration.

INSERT INTO tracks (
    id, slug, title, description, icon, display_order, published,
    content_version, created_at, updated_at, version
) VALUES (
    '019205a0-1000-7000-8000-000000000001',
    'angular-path',
    'The Angular Path',
    'Standalone components, signals and the modern Angular rendering model.',
    'angular',
    1,
    true,
    1,
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    0
);

INSERT INTO modules (
    id, track_id, title, display_order, estimated_minutes, created_at, updated_at, version
) VALUES
    (
        '019205a0-2000-7000-8000-000000000001',
        '019205a0-1000-7000-8000-000000000001',
        'Signals and Reactivity',
        1,
        55,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-2000-7000-8000-000000000002',
        '019205a0-1000-7000-8000-000000000001',
        'Routing and Navigation',
        2,
        60,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    );

INSERT INTO lessons (
    id, module_id, slug, title, body_markdown, difficulty, estimated_minutes,
    display_order, content_version, created_at, updated_at, version
) VALUES
    (
        '019205a0-3000-7000-8000-000000000001',
        '019205a0-2000-7000-8000-000000000001',
        'signals-and-reactivity',
        'Signals and Reactivity',
        E'## What is a signal?\n\nA signal is a value wrapper that records a dependency on whatever reads it.\nReading it inside a computation subscribes that computation; writing to it\nnotifies every subscriber.\n',
        'BEGINNER',
        25,
        1,
        1,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-3000-7000-8000-000000000002',
        '019205a0-2000-7000-8000-000000000001',
        'computed-and-effects',
        'Computed Values and Effects',
        E'## Derived state\n\nA computed signal recalculates only when one of the signals it read has\nchanged. An effect runs for its side effects and never produces a value.\n',
        'INTERMEDIATE',
        30,
        2,
        1,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-3000-7000-8000-000000000003',
        '019205a0-2000-7000-8000-000000000002',
        'route-configuration',
        'Route Configuration',
        E'## Declaring routes\n\nA route maps a URL path to a component and, optionally, to the data that\ncomponent needs before it renders.\n',
        'BEGINNER',
        25,
        1,
        1,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-3000-7000-8000-000000000004',
        '019205a0-2000-7000-8000-000000000002',
        'lazy-loading-routes',
        'Lazy Loading Routes',
        E'## Deferring work\n\nA lazily loaded route defers downloading its component until the route is\nactivated, which keeps the initial bundle small.\n',
        'ADVANCED',
        35,
        2,
        1,
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    );

-- The mind map's own identifier is what the manifest names as the entity id; a
-- client that keyed its local copy by track_id could not address it.
INSERT INTO mind_maps (
    id, track_id, root, content_version, created_at, updated_at, version
) VALUES (
    '019205a0-4000-7000-8000-000000000001',
    '019205a0-1000-7000-8000-000000000001',
    '{"id":"root","label":"The Angular Path","lesson_id":null,"children":[{"id":"reactivity","label":"Reactivity","lesson_id":null,"children":[{"id":"signals","label":"Signals","lesson_id":"019205a0-3000-7000-8000-000000000001","children":[]},{"id":"computed","label":"Computed and effects","lesson_id":"019205a0-3000-7000-8000-000000000002","children":[]}]},{"id":"routing","label":"Routing","lesson_id":null,"children":[{"id":"route-config","label":"Route configuration","lesson_id":"019205a0-3000-7000-8000-000000000003","children":[]},{"id":"lazy-routes","label":"Lazy loading","lesson_id":"019205a0-3000-7000-8000-000000000004","children":[]}]}]}'::jsonb,
    1,
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    TIMESTAMPTZ '2026-01-01 00:00:00+00',
    0
);
