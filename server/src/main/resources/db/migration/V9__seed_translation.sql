-- Gives the seeded content a real translation.
--
-- Why this is worth a migration of its own: without it, no test that runs
-- against seeded content ever sees a package that carries a translation, and a
-- field nobody exercises is a field nobody notices going wrong. A translated
-- body is not decoration in this schema -- it is a nested object inside the
-- hashed package bytes, it is what the "not yet translated" fallback is
-- measured against, and it is the one part of a package whose shape differs
-- between the entity's own columns and a translation row.
--
-- Two locales rather than one, on the same lesson, because the translations
-- array is sorted by locale before hashing. One entry cannot tell a correct
-- sort from no sort at all.
--
-- Identifiers and timestamps are fixed literals, like everything else in the
-- seed: a developer machine, CI and a fresh deployment must address the same
-- rows and re-running the chain elsewhere must not produce different data.
--
-- Bodies are written as escaped strings (E'...\n...') so the line endings of
-- this file cannot leak into stored content. A checkout that produced CRLF here
-- would otherwise store CRLF bodies and hash them differently from a checkout
-- that produced LF -- on one platform only, and only in the digest.

INSERT INTO content_translations (
    id, entity_type, entity_id, locale, title, body, created_at, updated_at, version
) VALUES
    (
        '019205a0-5000-7000-8000-000000000001',
        'LESSON',
        '019205a0-3000-7000-8000-000000000001',
        'tr',
        'Sinyaller ve Reaktivite',
        E'## Sinyal nedir?\n\nBir sinyal, kendisini okuyan hesaplamaya bagimlilik kaydeden bir deger\nsarmalayicisidir. Bir hesaplamanin icinde okunmasi o hesaplamayi abone yapar;\nuzerine yazilmasi butun aboneleri haberdar eder.\n',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    ),
    (
        '019205a0-5000-7000-8000-000000000002',
        'LESSON',
        '019205a0-3000-7000-8000-000000000001',
        'fr',
        'Signaux et reactivite',
        E'## Quest-ce quun signal ?\n\nUn signal est une enveloppe de valeur qui enregistre une dependance envers ce\nqui le lit. Le lire dans un calcul abonne ce calcul ; ecrire dedans notifie\ntous les abonnes.\n',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        TIMESTAMPTZ '2026-01-01 00:00:00+00',
        0
    );

-- A translation is part of the packaged bytes, so adding one is a content
-- change for the lesson that owns it, and a track's own counter changes on
-- anything a client would need to re-read beneath it. Both are plain counter
-- updates. Nothing here computes a digest: SQL cannot perform the text
-- normalization a digest depends on, and a computation placed in a migration is
-- one the determinism tests cannot reach.
UPDATE lessons
   SET content_version = content_version + 1
 WHERE id = '019205a0-3000-7000-8000-000000000001';

UPDATE tracks
   SET content_version = content_version + 1
 WHERE id = '019205a0-1000-7000-8000-000000000001';

-- The stored package for that lesson no longer describes it: both the
-- translation and the incremented counter are part of the hashed bytes. Clear
-- the three package columns and let the service layer, which owns the single
-- implementation of the packaging rules, rebuild them at startup. The columns
-- move together or not at all, which is what ck_lessons_package_complete
-- enforces, so all three are cleared in one statement.
UPDATE lessons
   SET sha256 = NULL,
       package_bytes = NULL,
       package_size_bytes = NULL
 WHERE id = '019205a0-3000-7000-8000-000000000001';
