\pset null '[null]'

CREATE TABLE author (id integer PRIMARY KEY, name text NOT NULL);
CREATE TABLE post   (id integer PRIMARY KEY, author_id integer NOT NULL,
                     title text NOT NULL, published boolean NOT NULL);

INSERT INTO author VALUES (1, 'ada'), (2, 'bo'), (3, 'cyd');
INSERT INTO post VALUES
    (10, 1, 'indexes', true),
    (11, 1, 'drafts',  false),
    (12, 2, 'nulls',   false);

-- Wanted: every author, with their published posts beside them.

-- ON decides which rows pair up. Rows that pair with nothing are still kept,
-- padded with unknowns, because that is what LEFT means.
SELECT a.name, p.title
FROM   author a
LEFT   JOIN post p ON p.author_id = a.id AND p.published
ORDER  BY a.name;

-- WHERE runs after the pairing, on the padded result. An unknown fails
-- `p.published`, every padded row is discarded, and the outer join has
-- silently become an inner one. No error, three authors down to one.
SELECT a.name, p.title
FROM   author a
LEFT   JOIN post p ON p.author_id = a.id
WHERE  p.published
ORDER  BY a.name;

-- The one predicate that does belong in WHERE after a LEFT JOIN is a test for
-- the padding itself. This is how "authors with no posts at all" is written.
SELECT a.name
FROM   author a
LEFT   JOIN post p ON p.author_id = a.id
WHERE  p.id IS NULL
ORDER  BY a.name;

SELECT
    (SELECT count(*) FROM author a LEFT JOIN post p
       ON p.author_id = a.id AND p.published)                 AS predicate_in_on,
    (SELECT count(*) FROM author a LEFT JOIN post p
       ON p.author_id = a.id WHERE p.published)               AS predicate_in_where,
    (SELECT count(*) FROM author a JOIN post p
       ON p.author_id = a.id AND p.published)                 AS plain_inner_join;

DROP TABLE post;
DROP TABLE author;
