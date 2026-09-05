-- Drops every stored content package so that the application rebuilds them on
-- its next start.
--
-- Why this exists: the packaged JSON changed shape. A translation entry inside a
-- lesson package now names its text `body` rather than `body_markdown`, matching
-- the (title, body) shape a translation row actually has, the name the read API
-- uses, and the name a track manifest already used. The lesson's own markdown is
-- still `body_markdown` at the top level of the package -- the two are different
-- fields and only one of them moved.
--
-- Changing the bytes changes the digest computed over them, so every stored
-- sha256 now describes a package the server would no longer produce. Leaving
-- them in place would be the exact failure the digest exists to prevent: a
-- manifest advertising one digest while the endpoint serves bytes that hash to
-- another.
--
-- Clearing rather than recomputing here is the point. A digest is never computed
-- by SQL -- not by a trigger, not by a generated column, not by a backfill
-- UPDATE -- because SQL cannot perform the text normalization the digest depends
-- on and a computation placed here is one the determinism tests cannot reach.
-- This migration only removes; the service layer, which owns the one
-- implementation of the packaging rules, puts the bytes back.
--
-- `content_version` is deliberately NOT touched. The content did not change,
-- only its serialization did. Incrementing it would tell every client that the
-- lesson was edited, which is false, and would cost each of them a re-download
-- of text they already have.
--
-- The three package columns move together or not at all, which is what
-- ck_lessons_package_complete and ck_mind_maps_package_complete enforce; that is
-- why all three are cleared in one statement rather than one at a time.

UPDATE lessons
   SET sha256 = NULL,
       package_bytes = NULL,
       package_size_bytes = NULL
 WHERE sha256 IS NOT NULL;

UPDATE mind_maps
   SET sha256 = NULL,
       package_bytes = NULL,
       package_size_bytes = NULL
 WHERE sha256 IS NOT NULL;
