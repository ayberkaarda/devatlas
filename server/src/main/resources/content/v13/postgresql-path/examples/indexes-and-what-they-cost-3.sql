SET max_parallel_workers_per_gather = 0;

CREATE TABLE progress (
    user_id   integer     NOT NULL,
    lesson_id integer     NOT NULL,
    done_at   timestamptz NOT NULL
);

INSERT INTO progress (user_id, lesson_id, done_at)
SELECT g % 1000, g % 300, timestamptz '2026-01-01 00:00:00+00' + (g || ' seconds')::interval
FROM   generate_series(1, 300000) AS g;

-- One index, two columns, and the order of the two is the whole decision.
CREATE INDEX ix_progress_user_lesson ON progress (user_id, lesson_id);
ANALYZE progress;

-- Leading column alone: the entries for one user sit together, so this is a
-- seek to a range.
EXPLAIN (COSTS OFF) SELECT count(*) FROM progress WHERE user_id = 42;

-- Both columns: a narrower range, answered without touching the table.
EXPLAIN (COSTS OFF) SELECT count(*) FROM progress WHERE user_id = 42 AND lesson_id = 7;

-- Second column alone. The index is still usable -- an equality on a later
-- column becomes a filter applied while every entry is read -- but there is no
-- range to seek to, so the whole index is scanned.
EXPLAIN (COSTS OFF) SELECT count(*) FROM progress WHERE lesson_id = 7;

-- With the columns the other way round, the same question is a seek again,
-- and the planner switches to the index that offers one.
CREATE INDEX ix_progress_lesson_user ON progress (lesson_id, user_id);
ANALYZE progress;

EXPLAIN (COSTS OFF) SELECT count(*) FROM progress WHERE lesson_id = 7;

-- Every index is also a write cost and a thing to keep. This one has never
-- answered a question, and the counter says so.
CREATE INDEX ix_progress_done_at ON progress (done_at);
ANALYZE progress;

SELECT indexrelname, idx_scan
FROM   pg_stat_user_indexes
WHERE  relname = 'progress'
ORDER  BY indexrelname;

DROP TABLE progress;
