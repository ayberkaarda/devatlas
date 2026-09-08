-- The rule you want to add is a rule the existing rows have never had to obey.
CREATE TABLE account (
    id      integer PRIMARY KEY,
    email   text    NOT NULL,
    balance numeric(10,2) NOT NULL
);

INSERT INTO account VALUES
    (1, 'ada@example.com',  10.00),
    (2, 'BO@example.com',   -5.00),
    (3, 'cyd@example.com',  20.00);

-- Adding the constraint outright scans the whole table and finds row 2.
ALTER TABLE account ADD CONSTRAINT ck_account_balance CHECK (balance >= 0);

-- NOT VALID records the rule without scanning. It binds every new row from
-- this moment and says nothing about the rows already there.
ALTER TABLE account ADD CONSTRAINT ck_account_balance CHECK (balance >= 0) NOT VALID;

SELECT conname, convalidated FROM pg_constraint WHERE conname = 'ck_account_balance';

-- New rows are refused.
INSERT INTO account VALUES (4, 'dee@example.com', -1.00);

-- So are updates to the old rows -- but only the rows an UPDATE touches.
UPDATE account SET balance = -6.00 WHERE id = 2;

-- The old row is still there and still negative.
SELECT id, balance FROM account WHERE balance < 0;

-- Validating asks the question the first ALTER asked, and gets the same answer
-- until the data is dealt with.
ALTER TABLE account VALIDATE CONSTRAINT ck_account_balance;

UPDATE account SET balance = 0 WHERE balance < 0;
ALTER TABLE account VALIDATE CONSTRAINT ck_account_balance;

SELECT conname, convalidated FROM pg_constraint WHERE conname = 'ck_account_balance';

-- A foreign key added to a table with rows behaves the same way, and the
-- second step is the one that reads every row.
CREATE TABLE payment (id integer PRIMARY KEY, account_id integer NOT NULL);
INSERT INTO payment VALUES (1, 1), (2, 99);

ALTER TABLE payment ADD CONSTRAINT fk_payment_account
    FOREIGN KEY (account_id) REFERENCES account (id) NOT VALID;

ALTER TABLE payment VALIDATE CONSTRAINT fk_payment_account;

DELETE FROM payment WHERE account_id = 99;
ALTER TABLE payment VALIDATE CONSTRAINT fk_payment_account;

SELECT conname, convalidated FROM pg_constraint WHERE conname = 'fk_payment_account';

DROP TABLE payment;
DROP TABLE account;
