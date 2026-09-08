-- A rule in application code guards the path that code takes. A constraint
-- guards every path, including UPDATE and bulk load.
CREATE TABLE lesson_row (
    id            integer     NOT NULL,
    slug          varchar(80) NOT NULL,
    difficulty    varchar(16) NOT NULL,
    display_order integer     NOT NULL,
    CONSTRAINT pk_lesson_row PRIMARY KEY (id),
    CONSTRAINT ck_lesson_row_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_lesson_row_difficulty
        CHECK (difficulty IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    CONSTRAINT ck_lesson_row_display_order CHECK (display_order BETWEEN 1 AND 10000)
);

INSERT INTO lesson_row VALUES (1, 'what-a-table-promises', 'BEGINNER', 1);

-- The insert path was never the only way in.
UPDATE lesson_row SET difficulty = 'EASY' WHERE id = 1;
UPDATE lesson_row SET display_order = 0 WHERE id = 1;
UPDATE lesson_row SET slug = 'What A Table Promises' WHERE id = 1;

-- Neither was the ORM. COPY is checked by the same constraints.
COPY lesson_row (id, slug, difficulty, display_order) FROM stdin;
2	keys	BEGINNER	2
3	Null Is Not A Value	BEGINNER	3
\.

SELECT id, slug, difficulty, display_order FROM lesson_row ORDER BY id;

DROP TABLE lesson_row;
