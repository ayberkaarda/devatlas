-- A natural key is a value that means something to people. That is exactly
-- why it changes.
CREATE TABLE account_natural (
    email varchar(254) NOT NULL,
    name  text         NOT NULL,
    CONSTRAINT pk_account_natural PRIMARY KEY (email)
);

CREATE TABLE payment_natural (
    id            integer      NOT NULL,
    account_email varchar(254) NOT NULL,
    amount        numeric(10,2) NOT NULL,
    CONSTRAINT pk_payment_natural PRIMARY KEY (id),
    CONSTRAINT fk_payment_natural FOREIGN KEY (account_email)
        REFERENCES account_natural (email)
);

INSERT INTO account_natural VALUES ('ada@old.example', 'Ada');
INSERT INTO payment_natural VALUES (1, 'ada@old.example', 10.00),
                                   (2, 'ada@old.example', 20.00);

-- She changes her address. The identity of the person did not change; the
-- value chosen to stand for it did.
UPDATE account_natural SET email = 'ada@new.example' WHERE email = 'ada@old.example';

-- The same key with a surrogate. The address is still unique, still the thing
-- people log in with, and no longer the thing rows point at.
CREATE TABLE account_surrogate (
    id    integer      NOT NULL,
    email varchar(254) NOT NULL,
    name  text         NOT NULL,
    CONSTRAINT pk_account_surrogate PRIMARY KEY (id),
    CONSTRAINT uq_account_surrogate_email UNIQUE (email)
);

CREATE TABLE payment_surrogate (
    id         integer       NOT NULL,
    account_id integer       NOT NULL,
    amount     numeric(10,2) NOT NULL,
    CONSTRAINT pk_payment_surrogate PRIMARY KEY (id),
    CONSTRAINT fk_payment_surrogate FOREIGN KEY (account_id)
        REFERENCES account_surrogate (id)
);

INSERT INTO account_surrogate VALUES (1, 'ada@old.example', 'Ada');
INSERT INTO payment_surrogate VALUES (1, 1, 10.00), (2, 1, 20.00);

UPDATE account_surrogate SET email = 'ada@new.example' WHERE id = 1;

SELECT a.email, count(p.id) AS payments, sum(p.amount) AS total
FROM   account_surrogate a
JOIN   payment_surrogate p ON p.account_id = a.id
GROUP  BY a.email;

DROP TABLE payment_surrogate;
DROP TABLE account_surrogate;
DROP TABLE payment_natural;
DROP TABLE account_natural;
