-- Withdraw the Angular track from publication now that its content has been
-- replaced.
--
-- The sample seed created this track published, which was right when it held
-- four short lessons written to give the read endpoints something real to
-- serve. The authored corpus rewrites it: nine lessons, three modules, a
-- different mind map, none of which any person has read.
--
-- The corpus loader deliberately never writes the published column on an
-- update, so that loading content cannot undo a publication decision a person
-- made. That protects a published track from the loader -- and, for this one
-- track, produces exactly the wrong outcome: the flag set for four sample
-- lessons would carry nine new ones into the product with nobody having
-- reviewed them. Approval attaches to content, not to a row, and this row's
-- content is not the content that was approved.
--
-- So the withdrawal is explicit and separate. Every other track in the corpus
-- arrives unpublished and is published by a later migration that a person
-- commits after reading it; this statement puts the one pre-existing track on
-- that same footing, rather than letting it skip the queue for the historical
-- reason that it happened to exist first.
--
-- Nothing is lost. The rows stay, the packages stay, and publication is one
-- UPDATE away once the track has been read.
UPDATE tracks
SET published   = false,
    updated_at  = TIMESTAMPTZ '2026-01-01 00:00:00+00',
    version     = version + 1
WHERE id = '019205a0-1000-7000-8000-000000000001'
  AND published;
