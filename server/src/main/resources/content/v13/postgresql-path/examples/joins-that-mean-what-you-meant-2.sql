CREATE TABLE "order"  (id integer PRIMARY KEY, customer text NOT NULL);
CREATE TABLE item     (id integer PRIMARY KEY, order_id integer NOT NULL, amount numeric(10,2) NOT NULL);
CREATE TABLE shipment (id integer PRIMARY KEY, order_id integer NOT NULL, carrier text NOT NULL);

INSERT INTO "order" VALUES (1, 'ada');
INSERT INTO item     VALUES (10, 1, 30.00), (11, 1, 20.00);
INSERT INTO shipment VALUES (20, 1, 'road'), (21, 1, 'air');

-- One order, fifty pounds of goods, two parcels.
SELECT sum(amount) AS true_order_total FROM item WHERE order_id = 1;

-- Join the order to its items: two rows, and the total is right.
SELECT o.customer, count(*) AS rows_returned, sum(i.amount) AS total
FROM   "order" o JOIN item i ON i.order_id = o.id
GROUP  BY o.customer;

-- Join it to its shipments as well. Two items times two shipments is four
-- rows, every amount appears twice, and the total doubles. Nothing errors.
SELECT o.customer, count(*) AS rows_returned, sum(i.amount) AS total
FROM   "order" o
JOIN   item i     ON i.order_id = o.id
JOIN   shipment s ON s.order_id = o.id
GROUP  BY o.customer;

-- Seen row by row, the duplication is obvious. It is the GROUP BY that hides it.
SELECT i.id AS item_id, i.amount, s.id AS shipment_id, s.carrier
FROM   "order" o
JOIN   item i     ON i.order_id = o.id
JOIN   shipment s ON s.order_id = o.id
ORDER  BY i.id, s.id;

-- Fix: aggregate each side to one row per order before joining them.
SELECT o.customer, i.total, i.items, s.parcels
FROM   "order" o
JOIN   (SELECT order_id, sum(amount) AS total, count(*) AS items FROM item GROUP BY order_id) i
       ON i.order_id = o.id
JOIN   (SELECT order_id, count(*) AS parcels FROM shipment GROUP BY order_id) s
       ON s.order_id = o.id;

-- Fix, written the other way: count the distinct things rather than the rows.
SELECT o.customer,
       sum(i.amount) / count(DISTINCT s.id) AS total_after_dividing_out,
       count(DISTINCT i.id) AS items,
       count(DISTINCT s.id) AS parcels
FROM   "order" o
JOIN   item i     ON i.order_id = o.id
JOIN   shipment s ON s.order_id = o.id
GROUP  BY o.customer;

DROP TABLE shipment;
DROP TABLE item;
DROP TABLE "order";
