-- Parallel workers are turned off so the printed plan is the same shape on
-- every machine. Nothing here depends on them.
SET max_parallel_workers_per_gather = 0;

CREATE TABLE event (
    id      integer NOT NULL,
    user_id integer NOT NULL,
    kind    text    NOT NULL
);

INSERT INTO event (id, user_id, kind)
SELECT g, g % 5000, CASE WHEN g % 100 = 0 THEN 'error' ELSE 'view' END
FROM   generate_series(1, 200000) AS g;

ANALYZE event;

-- No index. One matching row, and the only way to be sure of that is to look
-- at all two hundred thousand.
EXPLAIN (COSTS OFF) SELECT id FROM event WHERE user_id = 4242;

CREATE INDEX ix_event_user ON event (user_id);
ANALYZE event;

-- The same question, now answered from an ordered structure.
EXPLAIN (COSTS OFF) SELECT id FROM event WHERE user_id = 4242;

-- An index is only worth reading when it excludes most of the table. Here it
-- would exclude one row in a hundred, and the planner declines to use it.
CREATE INDEX ix_event_kind ON event (kind);
ANALYZE event;

EXPLAIN (COSTS OFF) SELECT count(*) FROM event WHERE kind = 'view';
EXPLAIN (COSTS OFF) SELECT count(*) FROM event WHERE kind = 'error';

DROP TABLE event;
