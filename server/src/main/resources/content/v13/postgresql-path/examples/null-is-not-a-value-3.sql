\pset null '[null]'

-- A CHECK refuses a row only when the condition is false. Unknown is not
-- false, so a nullable column slips past its own rule.
CREATE TABLE priced (
    sku   text NOT NULL,
    price numeric(10,2) CHECK (price > 0)
);

INSERT INTO priced VALUES ('a', 5.00);
INSERT INTO priced VALUES ('b', -5.00);
INSERT INTO priced VALUES ('c', NULL);

SELECT sku, price FROM priced ORDER BY sku;

DROP TABLE priced;

-- Concatenation and arithmetic propagate the unknown outward, so one absent
-- middle name empties the whole rendered string.
SELECT 'Ada' || ' ' || NULL || ' Lovelace' AS concatenated,
       concat('Ada', ' ', NULL, ' Lovelace') AS concat_function,
       10 + NULL AS arithmetic;

-- Sorting has to put unknowns somewhere. Ascending puts them last, descending
-- puts them first, and both defaults surprise someone.
CREATE TABLE score (name text, points integer);
INSERT INTO score VALUES ('ada', 30), ('bob', NULL), ('cyd', 10);

SELECT name, points FROM score ORDER BY points ASC;
SELECT name, points FROM score ORDER BY points DESC;
SELECT name, points FROM score ORDER BY points ASC NULLS FIRST;

-- Grouping and DISTINCT do treat two unknowns as the same, which is the exact
-- opposite of what the equality operator says about them.
INSERT INTO score VALUES ('dee', NULL);

SELECT points, count(*) AS rows_in_group FROM score GROUP BY points ORDER BY points NULLS LAST;
SELECT count(DISTINCT points) AS distinct_ignores_unknown,
       count(*) AS rows_total FROM score;

DROP TABLE score;
