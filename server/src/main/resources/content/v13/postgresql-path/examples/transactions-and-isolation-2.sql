-- The second connection is opened with dblink so the interleaving is fixed.
-- By hand this is two psql sessions typed in the order the comments give.
CREATE EXTENSION IF NOT EXISTS dblink;

CREATE TABLE balance (id integer PRIMARY KEY, amount numeric(10,2) NOT NULL);
SELECT dblink_connect('other', 'dbname=' || current_database()) AS second_session;

-- 1. Read, decide in the application, write back. Both sessions read 100.
INSERT INTO balance VALUES (1, 100.00);

BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT amount AS this_session_read FROM balance WHERE id = 1;

SELECT dblink_exec('other',
    'UPDATE balance SET amount = 100.00 - 50.00 WHERE id = 1') AS other_withdrew_50;

-- This session still believes the balance is 100 and writes 100 - 30.
UPDATE balance SET amount = 100.00 - 30.00 WHERE id = 1;
COMMIT;

SELECT amount AS after_two_withdrawals_of_50_and_30 FROM balance WHERE id = 1;

-- 2. The same interleaving, with the arithmetic done by the database. Under
-- READ COMMITTED an UPDATE re-reads a row another transaction just changed,
-- so the second subtraction starts from the value the first one left.
UPDATE balance SET amount = 100.00 WHERE id = 1;

BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT amount AS this_session_read FROM balance WHERE id = 1;

SELECT dblink_exec('other',
    'UPDATE balance SET amount = amount - 50.00 WHERE id = 1') AS other_withdrew_50;

UPDATE balance SET amount = amount - 30.00 WHERE id = 1;
COMMIT;

SELECT amount AS after_two_withdrawals_of_50_and_30 FROM balance WHERE id = 1;

-- 3. When the decision has to happen outside the database, say so when
-- reading. FOR UPDATE holds the row until this transaction ends; the other
-- session is told to wait rather than allowed to read a value about to change.
UPDATE balance SET amount = 100.00 WHERE id = 1;

BEGIN ISOLATION LEVEL READ COMMITTED;
SELECT amount AS locked_read FROM balance WHERE id = 1 FOR UPDATE;

-- NOWAIT reports the conflict instead of waiting for it. The failure reaches
-- this session as an error, and a savepoint is what lets the transaction carry
-- on afterwards rather than being abandoned.
SAVEPOINT before_other_session;
SELECT dblink_exec('other',
    'SELECT amount FROM balance WHERE id = 1 FOR UPDATE NOWAIT') AS other_tried_to_read;
ROLLBACK TO SAVEPOINT before_other_session;

UPDATE balance SET amount = 100.00 - 30.00 WHERE id = 1;
COMMIT;

SELECT amount AS after_one_withdrawal_of_30 FROM balance WHERE id = 1;

SELECT dblink_disconnect('other');
DROP TABLE balance;
DROP EXTENSION dblink;
