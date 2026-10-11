-- 第二版：订单允许已取消。连接到 bookmall_order 后执行。
--   Get-Content docs/sql/07-phase2-order.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_order

ALTER TABLE order_main DROP CONSTRAINT IF EXISTS ck_order_main_status;
ALTER TABLE order_main
    ADD CONSTRAINT ck_order_main_status CHECK (status IN (1, 2));

COMMENT ON COLUMN order_main.status IS '订单状态：1=已创建，2=已取消';
