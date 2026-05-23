-- Inserción de órdenes iniciales para pruebas
INSERT INTO orders (id, customer_id, total_amount, current_state, tracking_number) VALUES 
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 1, 500.00, 'CREADA', NULL),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 2, 750.00, 'ESPERANDO_PAGO', NULL),
('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13', 3, 1200.00, 'PAGADA', NULL);
