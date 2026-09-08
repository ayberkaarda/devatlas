CREATE TABLE ledger (
    id     integer PRIMARY KEY,
    day    date    NOT NULL,
    amount numeric(10,2) NOT NULL
);

-- Two entries share a day. That tie is what the rest of this listing is about.
INSERT INTO ledger VALUES
    (1, '2026-01-01', 10.00),
    (2, '2026-01-02', 20.00),
    (3, '2026-01-02', 30.00),
    (4, '2026-01-03', 40.00);

-- An aggregate with OVER keeps every row instead of collapsing them, so the
-- running total sits beside the value it accumulated.
SELECT id, day, amount,
       sum(amount) OVER (ORDER BY day, id) AS running_total
FROM   ledger
ORDER  BY day, id;

-- The default frame when ORDER BY is present is RANGE BETWEEN UNBOUNDED
-- PRECEDING AND CURRENT ROW, and RANGE means "every row whose ORDER BY value
-- ties with this one". Ordering by day alone therefore gives both entries on
-- the second day the same total: the frame swallowed the tie.
SELECT id, day, amount,
       sum(amount) OVER (ORDER BY day) AS running_total_by_range
FROM   ledger
ORDER  BY day, id;

-- ROWS counts rows instead of comparing values, so the tie is walked through
-- one entry at a time.
SELECT id, day, amount,
       sum(amount) OVER (ORDER BY day ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
           AS running_total_by_rows
FROM   ledger
ORDER  BY day, id;

-- PARTITION BY restarts the window; ORDER BY alone does not. Both totals are
-- computed over the same rows in the same pass.
SELECT id, day, amount,
       sum(amount) OVER (PARTITION BY day)                     AS day_total,
       sum(amount) OVER (ORDER BY day, id ROWS UNBOUNDED PRECEDING) AS running_total
FROM   ledger
ORDER  BY day, id;

DROP TABLE ledger;
