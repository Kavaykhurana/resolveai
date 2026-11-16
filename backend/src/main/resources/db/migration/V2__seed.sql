-- Demo accounts (password for both: demo1234)
INSERT INTO users (id, email, password_hash, name, role) VALUES
(1, 'customer@demo.com', '$2a$10$k1dYn.RocKUdFVKJsGnaZeSkJlQy12xLIx3sMCnBBbW9JAnC658PS', 'Aarav Sharma', 'CUSTOMER'),
(2, 'agent@demo.com',    '$2a$10$k1dYn.RocKUdFVKJsGnaZeSkJlQy12xLIx3sMCnBBbW9JAnC658PS', 'Priya (Support Agent)', 'AGENT');
SELECT setval('users_id_seq', 100);

-- ShopEase demo orders for the demo customer
INSERT INTO orders (order_number, customer_id, items, status, total_amount, ordered_at, delivered_at, tracking_id) VALUES
('SE-1001', 1, '[{"name":"Mechanical Keyboard","qty":1,"price":3499.00}]', 'PROCESSING', 3499.00, now() - interval '1 day',  NULL, NULL),
('SE-1002', 1, '[{"name":"Wireless Mouse","qty":1,"price":1299.00},{"name":"Mouse Pad XL","qty":1,"price":499.00}]', 'SHIPPED', 1798.00, now() - interval '3 days', NULL, 'TRK-88421-IN'),
('SE-1003', 1, '[{"name":"Wireless Headphones","qty":1,"price":2499.00}]', 'DELIVERED', 2499.00, now() - interval '9 days',  now() - interval '5 days', 'TRK-77120-IN'),
('SE-1004', 1, '[{"name":"Smart Watch","qty":1,"price":4999.00}]', 'DELIVERED', 4999.00, now() - interval '48 days', now() - interval '40 days', 'TRK-65001-IN'),
('SE-1005', 1, '[{"name":"USB-C Hub","qty":1,"price":1899.00}]', 'DELAYED', 1899.00, now() - interval '8 days', NULL, 'TRK-90312-IN'),
('SE-1006', 1, '[{"name":"Laptop Stand","qty":1,"price":1599.00}]', 'CANCELLED', 1599.00, now() - interval '12 days', NULL, NULL),
('SE-1007', 1, '[{"name":"Phone Case","qty":2,"price":399.00}]', 'DELIVERED', 798.00, now() - interval '5 days', now() - interval '2 days', 'TRK-91550-IN'),
('SE-1008', 1, '[{"name":"Bluetooth Speaker","qty":1,"price":2999.00}]', 'PROCESSING', 2999.00, now() - interval '6 hours', NULL, NULL);