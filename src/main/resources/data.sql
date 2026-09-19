-- =====================================================================
-- USERS
-- =====================================================================
INSERT INTO users (id, name, email) VALUES (1, 'Alice Johnson', 'alice.johnson@example.com');
INSERT INTO users (id, name, email) VALUES (2, 'Bob Smith', 'bob.smith@example.com');
INSERT INTO users (id, name, email) VALUES (3, 'Carla Mendes', 'carla.mendes@example.com');
INSERT INTO users (id, name, email) VALUES (4, 'David Lee', 'david.lee@example.com');
ALTER TABLE users ALTER COLUMN id RESTART WITH 5;

-- =====================================================================
-- PRODUCTS
-- =====================================================================
INSERT INTO products (id, name, description, price, stock_quantity) VALUES
    (1, 'Mechanical Keyboard', 'Tactile brown-switch mechanical keyboard', 89.99, 50);
INSERT INTO products (id, name, description, price, stock_quantity) VALUES
    (2, 'Wireless Mouse', 'Ergonomic wireless mouse with USB-C charging', 29.99, 120);
INSERT INTO products (id, name, description, price, stock_quantity) VALUES
    (3, '27-inch 4K Monitor', 'IPS panel, 4K UHD, HDR support', 349.50, 25);
INSERT INTO products (id, name, description, price, stock_quantity) VALUES
    (4, 'USB-C Docking Station', '10-in-1 hub with HDMI, Ethernet, and PD', 59.00, 75);
INSERT INTO products (id, name, description, price, stock_quantity) VALUES
    (5, 'Noise Cancelling Headphones', 'Over-ear ANC headphones, 30h battery', 199.99, 40);
INSERT INTO products (id, name, description, price, stock_quantity) VALUES
    (6, 'Standing Desk Converter', 'Height-adjustable desktop riser', 149.00, 15);
ALTER TABLE products ALTER COLUMN id RESTART WITH 7;

-- =====================================================================
-- ORDERS
-- =====================================================================
INSERT INTO orders (id, user_id, status, total_amount, created_at) VALUES
    (1, 1, 'DELIVERED', 119.98, '2026-08-01 10:15:30+00');
INSERT INTO orders (id, user_id, status, total_amount, created_at) VALUES
    (2, 1, 'SHIPPED', 349.50, '2026-09-05 14:22:00+00');
INSERT INTO orders (id, user_id, status, total_amount, created_at) VALUES
    (3, 2, 'PENDING', 259.98, '2026-09-15 09:05:12+00');
INSERT INTO orders (id, user_id, status, total_amount, created_at) VALUES
    (4, 3, 'CONFIRMED', 59.00, '2026-09-17 18:40:45+00');
INSERT INTO orders (id, user_id, status, total_amount, created_at) VALUES
    (5, 2, 'CANCELLED', 149.00, '2026-07-22 11:00:00+00');
ALTER TABLE orders ALTER COLUMN id RESTART WITH 6;

-- =====================================================================
-- ORDER ITEMS
-- =====================================================================
-- Order 1 (Alice, DELIVERED): keyboard + mouse
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (1, 1, 1, 1, 89.99);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (2, 1, 2, 1, 29.99);

-- Order 2 (Alice, SHIPPED): monitor
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (3, 2, 3, 1, 349.50);

-- Order 3 (Bob, PENDING): headphones + mouse
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (4, 3, 5, 1, 199.99);
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (5, 3, 2, 2, 29.99);

-- Order 4 (Carla, CONFIRMED): docking station
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (6, 4, 4, 1, 59.00);

-- Order 5 (Bob, CANCELLED): standing desk converter
INSERT INTO order_items (id, order_id, product_id, quantity, unit_price) VALUES (7, 5, 6, 1, 149.00);
ALTER TABLE order_items ALTER COLUMN id RESTART WITH 8;
