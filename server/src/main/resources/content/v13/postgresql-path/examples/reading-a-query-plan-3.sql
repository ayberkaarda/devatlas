-- This listing prints estimated row counts and cost numbers, and they are the
-- same on every run only because the table is small. ANALYZE inspects a random
-- sample of about 300 * default_statistics_target rows -- 30000 at the default
-- setting -- and a table below that size is read in full, so its statistics are
-- exact rather than sampled. The twenty thousand rows below sit under that
-- threshold deliberately.
--
-- Raising the row count past the sample size makes ANALYZE sample, the
-- statistics vary slightly between runs, and the rows= estimates that this
-- listing exists to compare against actual rows= stop being reproducible.

-- Timings are turned off because they differ on every machine. What is being
-- compared here is the estimate the planner made against the number of rows
-- that actually came back.
SET max_parallel_workers_per_gather = 0;

CREATE TABLE visit (
    id      integer NOT NULL,
    country text    NOT NULL,
    city    text    NOT NULL
);

-- City implies country. The planner does not know that.
INSERT INTO visit (id, country, city)
SELECT g,
       (ARRAY['TR','FR','DE','GB'])[1 + g % 4],
       (ARRAY['Istanbul','Paris','Berlin','London'])[1 + g % 4]
FROM   generate_series(1, 20000) AS g;

ANALYZE visit;

-- Each predicate alone is estimated well.
EXPLAIN (ANALYZE, TIMING OFF, SUMMARY OFF, BUFFERS OFF)
SELECT count(*) FROM visit WHERE country = 'TR';

EXPLAIN (ANALYZE, TIMING OFF, SUMMARY OFF, BUFFERS OFF)
SELECT count(*) FROM visit WHERE city = 'Istanbul';

-- Together, the planner assumes the two are independent and multiplies the
-- two fractions. A quarter of a quarter is a sixteenth, and the answer is a
-- quarter. This is the number to look for: rows=N against actual rows=M.
EXPLAIN (ANALYZE, TIMING OFF, SUMMARY OFF, BUFFERS OFF)
SELECT count(*) FROM visit WHERE country = 'TR' AND city = 'Istanbul';

-- Telling it the columns are related.
CREATE STATISTICS st_visit_country_city (dependencies) ON country, city FROM visit;
ANALYZE visit;

EXPLAIN (ANALYZE, TIMING OFF, SUMMARY OFF, BUFFERS OFF)
SELECT count(*) FROM visit WHERE country = 'TR' AND city = 'Istanbul';

DROP STATISTICS st_visit_country_city;
DROP TABLE visit;
