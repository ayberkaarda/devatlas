SET max_parallel_workers_per_gather = 0;

CREATE TABLE account (
    id    integer NOT NULL,
    email text    NOT NULL
);

INSERT INTO account (id, email)
SELECT g, 'User' || g || '@example.com' FROM generate_series(1, 100000) AS g;

CREATE INDEX ix_account_email ON account (email);
ANALYZE account;

-- This database was created with a non-C collation, which is what the
-- following two plans turn on.
SELECT datcollate FROM pg_database WHERE datname = current_database();

-- Equality matches the index exactly.
EXPLAIN (COSTS OFF) SELECT id FROM account WHERE email = 'User4242@example.com';

-- A function applied to the column. The index stores the column, not the
-- result of the function, so there is nothing to look the answer up in.
EXPLAIN (COSTS OFF) SELECT id FROM account WHERE lower(email) = 'user4242@example.com';

-- An index over the expression the query actually asks about.
CREATE INDEX ix_account_email_lower ON account (lower(email));
ANALYZE account;

EXPLAIN (COSTS OFF) SELECT id FROM account WHERE lower(email) = 'user4242@example.com';

-- A prefix pattern is a range, but only in an ordering where "starts with" and
-- "sorts between" are the same question. Under a linguistic collation they are
-- not, so the default index cannot serve it.
EXPLAIN (COSTS OFF) SELECT count(*) FROM account WHERE email LIKE 'User4242%';

-- text_pattern_ops sorts character by character, and then the range exists.
CREATE INDEX ix_account_email_pattern ON account (email text_pattern_ops);
ANALYZE account;

EXPLAIN (COSTS OFF) SELECT count(*) FROM account WHERE email LIKE 'User4242%';

-- No ordering of whole strings helps a pattern anchored at the other end.
EXPLAIN (COSTS OFF) SELECT count(*) FROM account WHERE email LIKE '%42@example.com';

DROP TABLE account;
