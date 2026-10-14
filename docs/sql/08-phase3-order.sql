-- 第三版：订单增加已支付。连接到 bookmall_order 后执行。
-- 已有 status=1 的订单视为二期「已创建即完成」，迁成 3（已支付）。
-- 新下单写入 1，表示待支付。
--   Get-Content docs/sql/08-phase3-order.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_order

ALTER TABLE order_main DROP CONSTRAINT IF EXISTS ck_order_main_status;

UPDATE order_main SET status = 3 WHERE status = 1;

ALTER TABLE order_main
    ADD CONSTRAINT ck_order_main_status CHECK (status IN (1, 2, 3));

COMMENT ON COLUMN order_main.status IS '订单状态：1=待支付，2=已取消，3=已支付。第三版将迁移前的 1 改为 3';
