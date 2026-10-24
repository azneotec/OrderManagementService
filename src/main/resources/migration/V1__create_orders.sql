CREATE TABLE orders (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    order_number        VARCHAR(32)    NOT NULL,
    user_id             BIGINT         NOT NULL,
    customer_email      VARCHAR(255)   NOT NULL,
    status              VARCHAR(32)    NOT NULL,
    payment_method      VARCHAR(16)    NOT NULL,
    payment_status      VARCHAR(20)    NOT NULL,
    payment_link        VARCHAR(512)   NULL,
    currency            VARCHAR(3)     NOT NULL,
    total_quantity      INT            NOT NULL,
    total_amount        DECIMAL(12, 2) NOT NULL,
    ship_full_name      VARCHAR(100)   NOT NULL,
    ship_phone_number   VARCHAR(20)    NOT NULL,
    ship_address_line1  VARCHAR(200)   NOT NULL,
    ship_address_line2  VARCHAR(200)   NULL,
    ship_city           VARCHAR(100)   NOT NULL,
    ship_state          VARCHAR(100)   NOT NULL,
    ship_postal_code    VARCHAR(20)    NOT NULL,
    ship_country        VARCHAR(100)   NOT NULL,
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    version             BIGINT         NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_number (order_number),
    KEY idx_orders_user_created (user_id, created_at)
);

CREATE TABLE order_item (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    order_id    BIGINT         NOT NULL,
    product_id  BIGINT         NOT NULL,
    title       VARCHAR(255)   NOT NULL,
    image_url   VARCHAR(512)   NULL,
    unit_price  DECIMAL(12, 2) NOT NULL,
    quantity    INT            NOT NULL,
    line_total  DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_item_order (order_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE TABLE order_tracking_event (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_id    BIGINT       NOT NULL,
    status      VARCHAR(32)  NOT NULL,
    note        VARCHAR(255) NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tracking_event_order (order_id),
    CONSTRAINT fk_tracking_event_order FOREIGN KEY (order_id) REFERENCES orders (id)
);
