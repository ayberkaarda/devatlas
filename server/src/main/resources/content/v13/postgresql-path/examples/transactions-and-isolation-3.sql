-- Two people, one rule: at least one of them stays on call. Each session
-- checks the rule, sees it holds, and then acts. The second connection is
-- opened with dblink so the interleaving is fixed; by hand it is two sessions.
CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TABLE duty (doctor text PRIMARY KEY, on_call boolean NOT NULL);
SELECT dblink_connect('other', 'dbname=' || current_database()) AS second_session;

-- 1. REPEATABLE READ. Neither transaction writes a row the other read, so
-- nothing conflicts, both commit, and the rule they both checked is broken.
INSERT INTO duty VALUES ('ada', true), ('bo', true);

BEGIN ISOLATION LEVEL REPEATABLE READ;
SELECT count(*) AS this_session_sees_on_call FROM duty WHERE on_call;

SELECT dblink_exec('other', 'BEGIN ISOLATION LEVEL REPEATABLE READ') AS other_begins;
SELECT other_sees_on_call FROM dblink('other',
    'SELECT count(*) FROM duty WHERE on_call') AS t(other_sees_on_call bigint);
SELECT dblink_exec('other', 'UPDATE duty SET on_call = false WHERE doctor = ''bo''') AS other_stands_down;
SELECT dblink_exec('other', 'COMMIT') AS other_commits;

UPDATE duty SET on_call = false WHERE doctor = 'ada';
COMMIT;

SELECT count(*) AS doctors_left_on_call FROM duty WHERE on_call;

-- 2. The same interleaving under SERIALIZABLE. Nothing about the statements
-- changed; the database now tracks that each transaction read what the other
-- wrote, and refuses the pair.
UPDATE duty SET on_call = true;

BEGIN ISOLATION LEVEL SERIALIZABLE;
SELECT count(*) AS this_session_sees_on_call FROM duty WHERE on_call;

SELECT dblink_exec('other', 'BEGIN ISOLATION LEVEL SERIALIZABLE') AS other_begins;
SELECT other_sees_on_call FROM dblink('other',
    'SELECT count(*) FROM duty WHERE on_call') AS t(other_sees_on_call bigint);
SELECT dblink_exec('other', 'UPDATE duty SET on_call = false WHERE doctor = ''bo''') AS other_stands_down;
SELECT dblink_exec('other', 'COMMIT') AS other_commits;

UPDATE duty SET on_call = false WHERE doctor = 'ada';
COMMIT;

-- SQLSTATE 40001 is not a bug report. It means "retry this transaction from
-- the beginning", and application code that uses SERIALIZABLE has to do so.
SELECT count(*) AS doctors_left_on_call FROM duty WHERE on_call;

SELECT dblink_disconnect('other');
DROP TABLE duty;
DROP EXTENSION dblink;
