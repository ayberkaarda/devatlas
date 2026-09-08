-- Isolation cannot be shown from one connection: a transaction always sees its
-- own writes. Open two psql sessions and interleave them by hand to feel it.
-- This listing opens the second connection with dblink instead, so that the
-- interleaving is fixed and the output is the same every time it is run.
CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TABLE seat (id integer PRIMARY KEY, taken boolean NOT NULL);
INSERT INTO seat VALUES (1, false);

SELECT dblink_connect('other', 'dbname=' || current_database()) AS second_session;

-- Proof that there really are two backends attached to this database.
SELECT count(*) AS backends_on_this_database
FROM   pg_stat_activity WHERE datname = current_database();

-- READ COMMITTED, the default. Each statement takes a fresh snapshot, so a
-- commit that lands between two statements becomes visible to the second one.
BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT current_setting('transaction_isolation') AS level, taken AS first_read
FROM   seat WHERE id = 1;

SELECT dblink_exec('other', 'UPDATE seat SET taken = true WHERE id = 1') AS other_session_did;

SELECT taken AS second_read FROM seat WHERE id = 1;
COMMIT;

-- Put it back and do exactly the same thing one level up.
UPDATE seat SET taken = false WHERE id = 1;

-- REPEATABLE READ takes one snapshot for the whole transaction, at the first
-- statement that reads anything. The other session's commit is real, and this
-- transaction still does not see it.
BEGIN ISOLATION LEVEL REPEATABLE READ;
SELECT current_setting('transaction_isolation') AS level, taken AS first_read
FROM   seat WHERE id = 1;

SELECT dblink_exec('other', 'UPDATE seat SET taken = true WHERE id = 1') AS other_session_did;

SELECT taken AS second_read FROM seat WHERE id = 1;
COMMIT;

-- Outside the transaction, the committed value was there all along.
SELECT taken AS after_commit FROM seat WHERE id = 1;

SELECT dblink_disconnect('other');
DROP TABLE seat;
-- Left exactly as it was found.
DROP EXTENSION dblink;
