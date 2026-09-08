\pset null '[null]'

CREATE TABLE lesson (
    id     integer NOT NULL,
    slug   text    NOT NULL,
    -- Unknown, not zero: nobody has estimated this one yet.
    minutes integer
);

CREATE TABLE flagged (lesson_id integer);

INSERT INTO lesson VALUES (1, 'keys', 7), (2, 'joins', NULL), (3, 'indexes', 9);
INSERT INTO flagged VALUES (1), (NULL);

-- The query everyone writes: "lessons nobody flagged". One unknown in the
-- subquery and the answer is empty, with no error and no warning.
SELECT count(*) AS not_in_result
FROM   lesson
WHERE  id NOT IN (SELECT lesson_id FROM flagged);

-- NOT EXISTS asks a different question and is not derailed by the unknown.
SELECT count(*) AS not_exists_result
FROM   lesson l
WHERE  NOT EXISTS (SELECT 1 FROM flagged f WHERE f.lesson_id = l.id);

-- IN is not symmetric with NOT IN here: a match still wins over an unknown.
SELECT count(*) AS in_result
FROM   lesson
WHERE  id IN (SELECT lesson_id FROM flagged);

-- Aggregates skip unknowns rather than treating them as zero, so the count
-- and the denominator of the average both shrink.
SELECT count(*)       AS rows_total,
       count(minutes) AS rows_with_minutes,
       sum(minutes)   AS total_minutes,
       avg(minutes)   AS average_minutes,
       sum(coalesce(minutes, 0)) AS total_counting_unknown_as_zero,
       avg(coalesce(minutes, 0)) AS average_counting_unknown_as_zero
FROM   lesson;

-- With no rows at all, sum is unknown and count is zero. They are not the
-- same answer, and a report that prints sum straight through shows a blank.
SELECT sum(minutes) AS sum_of_nothing, count(minutes) AS count_of_nothing
FROM   lesson WHERE slug = 'no-such-lesson';

DROP TABLE flagged;
DROP TABLE lesson;
