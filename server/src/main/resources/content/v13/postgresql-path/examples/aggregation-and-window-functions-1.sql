CREATE TABLE sale (
    id     integer PRIMARY KEY,
    region text    NOT NULL,
    seller text    NOT NULL,
    amount numeric(10,2) NOT NULL
);

INSERT INTO sale VALUES
    (1, 'north', 'ada', 100.00),
    (2, 'north', 'bo',   50.00),
    (3, 'south', 'cyd', 200.00),
    (4, 'south', 'cyd',  25.00),
    (5, 'east',  'dee',  10.00);

-- GROUP BY collapses rows. Every column in the select list must therefore be
-- either grouped or aggregated; there is no answer to "which seller" for a
-- group of two, so the database refuses rather than picking one.
SELECT region, seller, sum(amount) FROM sale GROUP BY region;

SELECT region, sum(amount) AS total, count(*) AS sales, max(seller) AS last_seller_alphabetically
FROM   sale
GROUP  BY region
ORDER  BY region;

-- WHERE filters rows before grouping; HAVING filters groups after. Swapping
-- them changes the answer, and only one of the two is even legal here.
SELECT region, sum(amount) AS total
FROM   sale
WHERE  amount >= 50.00
GROUP  BY region
ORDER  BY region;

SELECT region, sum(amount) AS total
FROM   sale
GROUP  BY region
HAVING sum(amount) >= 50.00
ORDER  BY region;

SELECT region, sum(amount) FROM sale WHERE sum(amount) >= 50.00 GROUP BY region;

-- Grouping by more than one column produces one row per combination present,
-- and never a row for a combination that is absent.
SELECT region, seller, sum(amount) AS total
FROM   sale
GROUP  BY region, seller
ORDER  BY region, seller;

DROP TABLE sale;
