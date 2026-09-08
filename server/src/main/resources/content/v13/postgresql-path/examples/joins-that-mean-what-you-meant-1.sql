\pset null '[null]'

-- Three authors, two of whom have written; one post has no author on file.
CREATE TABLE author (id integer PRIMARY KEY, name text NOT NULL);
CREATE TABLE post   (id integer PRIMARY KEY, author_id integer, title text NOT NULL);

INSERT INTO author VALUES (1, 'ada'), (2, 'bo'), (3, 'cyd');
INSERT INTO post VALUES
    (10, 1,    'indexes'),
    (11, 1,    'joins'),
    (12, 2,    'null'),
    (13, NULL, 'orphaned draft');

-- INNER: rows that matched on both sides. Cyd and the orphan are both gone.
SELECT a.name, p.title FROM author a JOIN post p ON p.author_id = a.id
ORDER BY a.name, p.title;

-- LEFT: every author, matched or not. Cyd comes back with unknowns beside her.
SELECT a.name, p.title FROM author a LEFT JOIN post p ON p.author_id = a.id
ORDER BY a.name, p.title;

-- RIGHT: every post, matched or not. The orphan comes back instead.
SELECT a.name, p.title FROM author a RIGHT JOIN post p ON p.author_id = a.id
ORDER BY p.title;

-- FULL: everything unmatched from either side.
SELECT a.name, p.title FROM author a FULL JOIN post p ON p.author_id = a.id
ORDER BY a.name, p.title;

SELECT
    (SELECT count(*) FROM author a JOIN       post p ON p.author_id = a.id) AS inner_rows,
    (SELECT count(*) FROM author a LEFT JOIN  post p ON p.author_id = a.id) AS left_rows,
    (SELECT count(*) FROM author a RIGHT JOIN post p ON p.author_id = a.id) AS right_rows,
    (SELECT count(*) FROM author a FULL JOIN  post p ON p.author_id = a.id) AS full_rows;

DROP TABLE post;
DROP TABLE author;
