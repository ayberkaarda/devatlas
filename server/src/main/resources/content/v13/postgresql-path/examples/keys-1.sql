-- A foreign key is a promise about a row somewhere else: it may not point at
-- nothing, and the row it points at may not quietly disappear.
CREATE TABLE track (
    id    integer     NOT NULL,
    slug  varchar(80) NOT NULL,
    CONSTRAINT pk_track PRIMARY KEY (id),
    CONSTRAINT uq_track_slug UNIQUE (slug)
);

CREATE TABLE module (
    id       integer      NOT NULL,
    track_id integer      NOT NULL,
    title    varchar(200) NOT NULL,
    CONSTRAINT pk_module PRIMARY KEY (id),
    CONSTRAINT fk_module_track FOREIGN KEY (track_id) REFERENCES track (id)
);

-- Cascade is a decision, not a default. Here the child is worth keeping only
-- while its parent exists, so deleting the module deletes its lessons.
CREATE TABLE lesson (
    id        integer     NOT NULL,
    module_id integer     NOT NULL,
    slug      varchar(80) NOT NULL,
    CONSTRAINT pk_lesson PRIMARY KEY (id),
    CONSTRAINT fk_lesson_module FOREIGN KEY (module_id)
        REFERENCES module (id) ON DELETE CASCADE
);

INSERT INTO track VALUES (1, 'postgresql-path');
INSERT INTO module VALUES (10, 1, 'Foundations');
INSERT INTO lesson VALUES (100, 10, 'a-table-is-a-promise'), (101, 10, 'keys');

-- A child of a parent that does not exist.
INSERT INTO module VALUES (11, 99, 'Orphan');

-- A parent with children, deleted. The default action is NO ACTION, which
-- refuses; nothing here silently loses a row.
DELETE FROM track WHERE id = 1;

-- The module's own children were declared ON DELETE CASCADE, so this one works
-- and takes two lessons with it.
DELETE FROM module WHERE id = 10;

SELECT (SELECT count(*) FROM track)  AS tracks,
       (SELECT count(*) FROM module) AS modules,
       (SELECT count(*) FROM lesson) AS lessons;

DROP TABLE lesson;
DROP TABLE module;
DROP TABLE track;
