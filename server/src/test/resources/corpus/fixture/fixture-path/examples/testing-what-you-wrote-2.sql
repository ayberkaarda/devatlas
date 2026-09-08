CREATE TABLE order_totals (
    id SERIAL PRIMARY KEY,
    customer_name TEXT NOT NULL,
    total_cents INTEGER NOT NULL
);

INSERT INTO order_totals (customer_name, total_cents)
VALUES ('ada', 1200), ('grace', 700);

SELECT customer_name, total_cents
FROM order_totals
ORDER BY total_cents DESC;
