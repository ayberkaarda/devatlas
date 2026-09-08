-- UNIQUE says "no two rows agree here". Two rows that are both unknown do not
-- agree, so by default they do not collide.
CREATE TABLE contact (
    id    integer NOT NULL,
    phone text,
    CONSTRAINT pk_contact PRIMARY KEY (id),
    CONSTRAINT uq_contact_phone UNIQUE (phone)
);

INSERT INTO contact VALUES (1, NULL), (2, NULL), (3, '555-0100');
INSERT INTO contact VALUES (4, '555-0100');

SELECT count(*) AS rows_with_unknown_phone FROM contact WHERE phone IS NULL;

DROP TABLE contact;

-- PostgreSQL 15 added NULLS NOT DISTINCT, which asks for the other reading.
CREATE TABLE contact_strict (
    id    integer NOT NULL,
    phone text,
    CONSTRAINT pk_contact_strict PRIMARY KEY (id),
    CONSTRAINT uq_contact_strict_phone UNIQUE NULLS NOT DISTINCT (phone)
);

INSERT INTO contact_strict VALUES (1, NULL);
INSERT INTO contact_strict VALUES (2, NULL);

DROP TABLE contact_strict;

-- A soft-deleted row keeps its slug forever under a plain unique index, so the
-- address can never be reused. A partial index scopes uniqueness to live rows.
CREATE TABLE lesson (
    id         integer     NOT NULL,
    slug       varchar(80) NOT NULL,
    deleted_at timestamptz,
    CONSTRAINT pk_lesson PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_lesson_slug ON lesson (slug) WHERE deleted_at IS NULL;

INSERT INTO lesson VALUES (1, 'keys', NULL);
UPDATE lesson SET deleted_at = '2026-01-01T00:00:00Z' WHERE id = 1;

-- Allowed: the old row no longer participates in the index.
INSERT INTO lesson VALUES (2, 'keys', NULL);

-- Still refused: two live rows may not share a slug.
INSERT INTO lesson VALUES (3, 'keys', NULL);

SELECT id, slug, (deleted_at IS NULL) AS live FROM lesson ORDER BY id;

DROP TABLE lesson;
