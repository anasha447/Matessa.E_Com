-- Products without variations still need stock (runs once per store schema).
--
-- Stock stays on product_variants — one stock path for cart, checkout and reservations. A
-- product with no variations carries ONE hidden "default" variant that holds its stock. The
-- first real variation deactivates it; removing the last real variation brings it back.

alter table product_variants add column is_default boolean not null default false;

-- At most one default variant per product.
create unique index ux_variants_one_default on product_variants (product_id) where is_default;

-- Existing products that have no variant at all get their default variant (out of stock until
-- an admin sets the product's stock).
insert into product_variants (product_id, sku, stock_quantity, active, is_default)
select p.id, 'P' || p.id || '-DEFAULT', 0, true, true
from products p
where not exists (select 1 from product_variants v where v.product_id = p.id);
