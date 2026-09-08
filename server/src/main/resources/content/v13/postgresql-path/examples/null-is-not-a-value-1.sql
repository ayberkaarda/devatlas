-- psql prints an unknown as an empty cell by default, which is precisely the
-- confusion this lesson is about. Give it a visible name first.
\pset null '[null]'

-- SQL logic has three values, not two. AND and OR both have to say what
-- happens when one side is unknown.
SELECT a::text AS a, b::text AS b,
       (a AND b)::text AS "a AND b",
       (a OR  b)::text AS "a OR b",
       (NOT a)::text   AS "NOT a"
FROM   (VALUES (true), (false), (NULL)) AS x(a)
CROSS  JOIN (VALUES (true), (false), (NULL)) AS y(b)
ORDER  BY a IS NULL, a DESC, b IS NULL, b DESC;

-- Equality with an unknown is unknown, not false, and WHERE keeps only rows
-- whose condition is true.
CREATE TABLE reading (id integer, value integer);
INSERT INTO reading VALUES (1, 10), (2, NULL), (3, 30);

SELECT 'value = NULL'          AS predicate, count(*) AS rows_kept FROM reading WHERE value = NULL
UNION ALL
SELECT 'value <> NULL',        count(*) FROM reading WHERE value <> NULL
UNION ALL
SELECT 'value IS NULL',        count(*) FROM reading WHERE value IS NULL
UNION ALL
SELECT 'value IS NOT NULL',    count(*) FROM reading WHERE value IS NOT NULL
UNION ALL
SELECT 'value IS DISTINCT FROM 10', count(*) FROM reading WHERE value IS DISTINCT FROM 10;

DROP TABLE reading;
