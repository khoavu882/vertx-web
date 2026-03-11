-- V1: Create product table
-- Matches the Product domain record fields.
-- product_id is a client-generated UUID string (VARCHAR 36).

CREATE TABLE IF NOT EXISTS tbl_product
(
    product_id VARCHAR(36)   NOT NULL,
    name       VARCHAR(255)  NOT NULL,
    category   VARCHAR(100)  NOT NULL,
    price      DECIMAL(10,2) NOT NULL,
    quantity   INT           NOT NULL DEFAULT 0,
    in_stock   BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (product_id),
    INDEX idx_tbl_product_category (category),
    INDEX idx_tbl_product_in_stock (in_stock)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
