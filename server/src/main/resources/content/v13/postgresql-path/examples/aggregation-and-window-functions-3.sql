\pset null '[null]'

CREATE TABLE sale (
    id     integer PRIMARY KEY,
    region text    NOT NULL,
    seller text    NOT NULL,
    amount numeric(10,2) NOT NULL
);

INSERT INTO sale VALUES
    (1, 'north', 'ada', 100.00),
    (2, 'north', 'bo',  100.00),
    (3, 'north', 'cyd',  40.00),
    (4, 'south', 'dee', 200.00),
    (5, 'south', 'eve',  75.00);

-- Three numberings, three different answers about a tie. rank leaves a gap
-- after it, dense_rank does not, and row_number refuses to tie at all -- which
-- is why its window has to order by something unique.
SELECT region, seller, amount,
       rank()       OVER (PARTITION BY region ORDER BY amount DESC)     AS rank,
       dense_rank() OVER (PARTITION BY region ORDER BY amount DESC)     AS dense_rank,
       row_number() OVER (PARTITION BY region ORDER BY amount DESC, id) AS row_number
FROM   sale
ORDER  BY region, amount DESC, id;

-- A window function is evaluated after WHERE, so WHERE cannot see it.
SELECT region, seller FROM sale
WHERE row_number() OVER (PARTITION BY region ORDER BY amount DESC, id) = 1;

-- Compute it in a subquery, then filter the result of that.
SELECT region, seller, amount
FROM   (SELECT region, seller, amount,
               row_number() OVER (PARTITION BY region ORDER BY amount DESC, id) AS n
        FROM   sale) ranked
WHERE  n = 1
ORDER  BY region;

-- lag reaches into the previous row of the window. The first row of each
-- partition has no previous row, and the difference there is unknown, not zero.
SELECT region, seller, amount,
       lag(amount) OVER (PARTITION BY region ORDER BY amount DESC, id) AS previous_amount,
       amount - lag(amount) OVER (PARTITION BY region ORDER BY amount DESC, id) AS difference
FROM   sale
ORDER  BY region, amount DESC, id;

-- Share of the partition, without a second pass over the table.
SELECT region, seller,
       round(100 * amount / sum(amount) OVER (PARTITION BY region), 1) AS percent_of_region
FROM   sale
ORDER  BY region, amount DESC, id;

DROP TABLE sale;
