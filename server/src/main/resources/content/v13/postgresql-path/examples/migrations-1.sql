-- A migration on an empty table is a text edit. A migration on a table that is
-- already holding rows has to say what happens to those rows.
CREATE TABLE lesson (
    id   integer PRIMARY KEY,
    slug text    NOT NULL
);

INSERT INTO lesson SELECT g, 'lesson-' || g FROM generate_series(1, 50000) AS g;

-- A rewrite copies every row into a new file on disk and holds an exclusive
-- lock while it does. Comparing the file behind the table before and after is
-- how to tell whether one happened.
CREATE TABLE filenode_before AS SELECT pg_relation_filenode('lesson') AS node;

-- Since PostgreSQL 11 a constant default is recorded as metadata: existing
-- rows are not touched, and the value is filled in as they are read.
ALTER TABLE lesson ADD COLUMN difficulty text NOT NULL DEFAULT 'BEGINNER';

SELECT (SELECT node FROM filenode_before) = pg_relation_filenode('lesson')
       AS constant_default_left_the_file_alone;

-- A default that has to be evaluated per row cannot be metadata, so the table
-- is rewritten.
ALTER TABLE lesson ADD COLUMN external_id uuid NOT NULL DEFAULT gen_random_uuid();

SELECT (SELECT node FROM filenode_before) = pg_relation_filenode('lesson')
       AS volatile_default_left_the_file_alone;

DROP TABLE filenode_before;

-- Adding a NOT NULL column with no default to a table with rows: there is no
-- value to put in the fifty thousand rows that already exist.
ALTER TABLE lesson ADD COLUMN author_id integer NOT NULL;

-- The three-step version. Add it nullable, fill it in, then make the promise.
ALTER TABLE lesson ADD COLUMN author_id integer;
UPDATE lesson SET author_id = 1 WHERE author_id IS NULL;
ALTER TABLE lesson ALTER COLUMN author_id SET NOT NULL;

SELECT count(*) AS rows_total, count(author_id) AS rows_with_author FROM lesson;

DROP TABLE lesson;
