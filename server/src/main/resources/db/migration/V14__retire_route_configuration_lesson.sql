-- Retire the one sample lesson the Angular corpus does not succeed.
--
-- The sample seed created four Angular lessons. The authored corpus reuses three
-- of those identifiers, so those three rows are rewritten in place and every
-- completion already recorded against them still points at the lesson a reader
-- finished. The fourth, 'route-configuration', has no successor: the authored
-- track covers lazy loading but not route declaration, so nothing claims that
-- identifier.
--
-- Left alone, the row survives the corpus load attached to a module that has
-- been retitled around it -- a routing lesson sitting inside "The reactive
-- model", listed by the read paths and advertised by the manifest, teaching a
-- subject the track no longer covers.
--
-- Soft delete rather than DELETE. The lessons table carries deleted_at for
-- exactly this case: user_progress references lessons, and a completion is a
-- fact about a person's history that an editorial decision must not erase.
-- Every read path, the manifest and the progress-sync reference check already
-- scope to deleted_at IS NULL, so setting it is the whole change. The package
-- columns are deliberately left as they are, because packaging state belongs to
-- the row rather than to its visibility and a restore should not bring the
-- lesson back undownloadable.
--
-- Addressed by identifier, not by slug: the slug is what an author controls and
-- could reuse, while the identifier is what the sample seed fixed as a literal
-- so that every environment addresses the same row.
UPDATE lessons
SET deleted_at = TIMESTAMPTZ '2026-01-01 00:00:00+00',
    updated_at = TIMESTAMPTZ '2026-01-01 00:00:00+00',
    version    = version + 1
WHERE id = '019205a0-3000-7000-8000-000000000003'
  AND deleted_at IS NULL;
