-- Disables 'OpenJDK Releases' (seeded in V10): its GitHub tags read "jdk-26+14", with no
-- dot anywhere in the numeric part, and every version string this pipeline extracts requires
-- one ("v?\d+(\.\d+){1,3}", §5.7). The source has therefore never yielded a single candidate
-- version string since it was seeded -- every scheduled sweep fetches it, finds nothing to
-- extract from any entry, and moves on, forever.
--
-- Two ways to fix this were considered. Widening the extractor's shape to also accept a
-- dotless run of digits was rejected: that shape is common English ("Java 21", "phase 2", a
-- year, a port number) inside a title or excerpt that carries no version at all, so the same
-- change that makes "26+14" extractable would make the extractor confidently wrong on
-- unrelated text across every other whitelisted feed, for a source it would still not verify
-- afterward -- verify_url_pattern below is a GitHub Releases tag URL, and OpenJDK's own
-- release notes (like Go's and PostgreSQL's, per V11) are not on GitHub tags at all, so a
-- wider extractor still could not confirm what it found. Disabling the row is the narrow
-- fix: it stops six-hourly requests to a feed that can never produce a draft, without
-- touching the shared regex every other source depends on.
--
-- Re-enabling this source later needs a verify_url_pattern this pipeline can actually confirm
-- against (OpenJDK's own download/release-notes page, the way Go's and PostgreSQL's rows
-- already do), not merely an extractor change.

UPDATE whitelist_sources
SET enabled = false,
    updated_at = TIMESTAMPTZ '2026-09-06 00:00:00+00',
    version = version + 1
WHERE id = '019205a0-6000-7000-8000-000000000003';
