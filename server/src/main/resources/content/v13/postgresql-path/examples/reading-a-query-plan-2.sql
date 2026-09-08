-- This listing prints cost numbers, and they are the same on every run only
-- because the table is small. ANALYZE inspects a random sample of about
-- 300 * default_statistics_target rows -- 30000 at the default setting -- and a
-- table below that size is read in full, so its statistics are exact rather
-- than sampled, and every cost derived from them is identical each time.
--
-- Raising the row count past that sample size makes ANALYZE sample, the
-- statistics vary slightly between runs, and these printed costs stop matching.
-- If a larger table is wanted here, drop the costs with COSTS OFF and compare
-- plan shapes instead.

-- A sequential scan is not a diagnosis. On a table that fits in a handful of
-- pages it is the cheapest way to answer almost anything.
SET max_parallel_workers_per_gather = 0;

CREATE TABLE track (
    id            integer     NOT NULL,
    slug          varchar(80) NOT NULL,
    published     boolean     NOT NULL,
    display_order integer     NOT NULL,
    CONSTRAINT pk_track PRIMARY KEY (id)
);

INSERT INTO track
SELECT g, 'track-' || g, g % 3 = 0, g FROM generate_series(1, 500) AS g;

CREATE INDEX ix_track_published_order ON track (published, display_order);
ANALYZE track;

SELECT relpages AS pages_in_table, reltuples::bigint AS rows_in_table
FROM   pg_class WHERE relname = 'track';

-- The index exists, matches the predicate, and is not used. Reading three
-- pages start to finish costs less than walking an index and then fetching
-- from those same three pages anyway.
EXPLAIN SELECT id, slug FROM track WHERE published ORDER BY display_order;

-- The same query with the sequential scan taken away. This is the plan the
-- planner rejected, and its estimated cost says why.
SET enable_seqscan = off;
EXPLAIN SELECT id, slug FROM track WHERE published ORDER BY display_order;
RESET enable_seqscan;

-- Selectivity, not table size, is the thing that changes the answer. One row
-- out of five hundred is worth an index; a third of them is not.
EXPLAIN (COSTS OFF) SELECT id, slug FROM track WHERE id = 250;

DROP TABLE track;
