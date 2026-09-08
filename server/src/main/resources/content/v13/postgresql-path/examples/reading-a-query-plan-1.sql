-- A plan is a tree, printed with the root at the top. It is read from the
-- most-indented line outwards: those nodes run first and hand rows upwards.
SET max_parallel_workers_per_gather = 0;

CREATE TABLE author (
    id   integer NOT NULL,
    name text    NOT NULL,
    CONSTRAINT pk_author PRIMARY KEY (id)
);

CREATE TABLE post (
    id        integer NOT NULL,
    author_id integer NOT NULL,
    views     integer NOT NULL,
    CONSTRAINT pk_post PRIMARY KEY (id)
);

INSERT INTO author SELECT g, 'author-' || g FROM generate_series(1, 2000) AS g;
INSERT INTO post   SELECT g, 1 + (g % 2000), g % 997 FROM generate_series(1, 20000) AS g;

CREATE INDEX ix_post_author ON post (author_id);
ANALYZE author;
ANALYZE post;

-- One author's posts. Two small reads joined one row at a time.
EXPLAIN (COSTS OFF)
SELECT a.name, p.views
FROM   author a
JOIN   post p ON p.author_id = a.id
WHERE  a.id = 7;

-- Every author's posts. Reading both tables once and building a hash beats
-- twenty thousand index lookups, so the same query text gets a different tree.
EXPLAIN (COSTS OFF)
SELECT a.name, sum(p.views) AS total
FROM   author a
JOIN   post p ON p.author_id = a.id
GROUP  BY a.name;

-- Sorting is its own node, and it appears whether or not you asked for it.
EXPLAIN (COSTS OFF)
SELECT author_id, count(*)
FROM   post
GROUP  BY author_id
ORDER  BY count(*) DESC
LIMIT  5;

DROP TABLE post;
DROP TABLE author;
