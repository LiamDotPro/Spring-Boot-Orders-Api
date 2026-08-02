-- Seed stock so the application is usable against a cold database.
--
-- Runs on every startup, so every statement is idempotent: ON CONFLICT DO NOTHING against the
-- unique index on sku. That means editing a price here will NOT update an existing row — change
-- it through the API, or drop the table and let it reseed.
--
-- Two settings in application.yml make this file run at all:
--   spring.sql.init.mode: always            (defaults to embedded-only; Postgres is not embedded)
--   spring.jpa.defer-datasource-initialization: true
--                                           (or this runs before Hibernate creates the tables)
--
-- The id/audit columns are populated by hand because nothing here goes through JPA, so neither the
-- UUID generator nor the auditing listener on BaseEntity gets a say.

INSERT INTO stock_items (id, created_at, updated_at, version, sku, description, unit_price, quantity_on_hand, quantity_allocated)
VALUES (gen_random_uuid(), now(), now(), 0, 'SKU-1', 'Bookcase', 320.99, 1, 0)
ON CONFLICT (sku) DO NOTHING;

INSERT INTO stock_items (id, created_at, updated_at, version, sku, description, unit_price, quantity_on_hand, quantity_allocated)
VALUES (gen_random_uuid(), now(), now(), 0, 'SKU-2', 'Shelf', 11.99, 11, 0)
ON CONFLICT (sku) DO NOTHING;

INSERT INTO stock_items (id, created_at, updated_at, version, sku, description, unit_price, quantity_on_hand, quantity_allocated)
VALUES (gen_random_uuid(), now(), now(), 0, 'SKU-3', 'Wardrobe', 33.99, 3, 0)
ON CONFLICT (sku) DO NOTHING;
