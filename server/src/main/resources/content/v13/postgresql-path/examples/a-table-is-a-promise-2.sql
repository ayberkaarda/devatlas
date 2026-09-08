-- Choosing the type is choosing what the column can and cannot hold.

-- Binary floating point cannot represent a tenth. numeric can.
SELECT 0.1::double precision + 0.2::double precision = 0.3::double precision AS float_agrees,
       0.1::numeric          + 0.2::numeric          = 0.3::numeric          AS numeric_agrees;

CREATE TABLE priced (
    sku   text          NOT NULL,
    price numeric(10,2) NOT NULL
);

-- Scale is enforced by rounding, silently. Precision is enforced by refusing.
INSERT INTO priced VALUES ('rounded', 12.345);
INSERT INTO priced VALUES ('too-big', 123456789.01);

SELECT sku, price FROM priced ORDER BY sku;

DROP TABLE priced;

-- timestamp records the reading on a wall clock; timestamptz records the
-- instant. Only one of them survives being read in another time zone.
SET TIME ZONE 'UTC';

CREATE TABLE recorded (
    label text        NOT NULL,
    naive timestamp   NOT NULL,
    aware timestamptz NOT NULL
);

INSERT INTO recorded VALUES
    ('release', '2026-03-29 02:30:00', '2026-03-29 02:30:00+01');

SELECT label, naive, aware FROM recorded;

SET TIME ZONE 'Europe/Istanbul';

SELECT label, naive, aware FROM recorded;

RESET TIME ZONE;
DROP TABLE recorded;
