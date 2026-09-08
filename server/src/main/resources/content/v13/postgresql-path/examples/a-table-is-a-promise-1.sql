-- A table declaration is a set of promises the database keeps on every write
-- path, including the ones the application does not know about.
CREATE TABLE account (
    id     uuid         NOT NULL,
    email  varchar(254) NOT NULL,
    role   varchar(16)  NOT NULL,
    locale varchar(2)   NOT NULL DEFAULT 'en',
    CONSTRAINT pk_account PRIMARY KEY (id),
    CONSTRAINT uq_account_email UNIQUE (email),
    -- Addresses are matched case-insensitively by storing them lowercased,
    -- so the uniqueness rule and every lookup agree on one spelling.
    CONSTRAINT ck_account_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_account_role CHECK (role IN ('ADMIN', 'EDITOR', 'USER'))
);

INSERT INTO account (id, email, role)
VALUES ('11111111-1111-4111-8111-111111111111', 'ada@example.com', 'ADMIN');

-- Same address in a different case. The CHECK refuses it before UNIQUE is
-- ever consulted, which is why there is a CHECK and not only an index.
INSERT INTO account (id, email, role)
VALUES ('22222222-2222-4222-8222-222222222222', 'Ada@example.com', 'USER');

-- Exactly the same address. Now UNIQUE is the one that answers.
INSERT INTO account (id, email, role)
VALUES ('33333333-3333-4333-8333-333333333333', 'ada@example.com', 'USER');

-- A role nobody defined.
INSERT INTO account (id, email, role)
VALUES ('44444444-4444-4444-8444-444444444444', 'grace@example.com', 'ROOT');

-- No address at all.
INSERT INTO account (id, email, role)
VALUES ('55555555-5555-4555-8555-555555555555', NULL, 'USER');

-- The column is two characters wide, and that width is part of the promise.
INSERT INTO account (id, email, role, locale)
VALUES ('66666666-6666-4666-8666-666666666666', 'linus@example.com', 'USER', 'tur');

SELECT email, role, locale FROM account ORDER BY email;

DROP TABLE account;
