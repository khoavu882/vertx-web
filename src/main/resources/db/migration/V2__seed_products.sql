-- V2: Seed initial products for development
-- Uses the same IDs as the former in-memory stub for test compatibility.
-- Remove or replace this migration in production environments.

INSERT INTO tbl_product (product_id, name, category, price, quantity, in_stock)
VALUES ('1', 'Widget',    'Hardware',    9.99,  100, TRUE),
       ('2', 'Gadget',    'Electronics', 49.99, 25,  TRUE),
       ('3', 'Doohickey', 'Accessories', 4.99,  0,   FALSE)
ON DUPLICATE KEY UPDATE name = VALUES(name);
