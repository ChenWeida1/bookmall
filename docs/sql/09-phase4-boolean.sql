-- 第四期：把只有两个取值的列改成 boolean。
-- 不改 user_account.role（以后可能有更多角色）和 order_main.status（三种订单状态）。
--
-- 图书库：
--   Get-Content docs/sql/09-phase4-boolean.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_book
-- 通知库只执行文件后半段（从 \connect 之后，或单独执行通知段）。
-- 本脚本按库拆成两段，用 psql 的 \connect 切换。从 postgres 库执行：
--   Get-Content docs/sql/09-phase4-boolean.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d postgres

\connect bookmall_book

ALTER TABLE book DROP CONSTRAINT IF EXISTS ck_book_status;
ALTER TABLE book RENAME COLUMN status TO on_sale;
ALTER TABLE book
    ALTER COLUMN on_sale TYPE boolean
    USING (on_sale::int = 1);
ALTER TABLE book ALTER COLUMN on_sale SET NOT NULL;
COMMENT ON COLUMN book.on_sale IS '是否在售。true=在售，false=下架';

ALTER TABLE stock_change DROP CONSTRAINT IF EXISTS ck_stock_change_kind;
ALTER TABLE stock_change RENAME COLUMN kind TO deducted;
ALTER TABLE stock_change
    ALTER COLUMN deducted TYPE boolean
    USING (deducted::int = 1);
ALTER TABLE stock_change ALTER COLUMN deducted SET NOT NULL;
COMMENT ON COLUMN stock_change.deducted IS 'true=已扣减，false=已回补';

\connect bookmall_notification

ALTER TABLE notification DROP CONSTRAINT IF EXISTS ck_notification_read_flag;
ALTER TABLE notification
    ALTER COLUMN read_flag TYPE boolean
    USING (read_flag::int = 1);
ALTER TABLE notification ALTER COLUMN read_flag SET NOT NULL;
ALTER TABLE notification ALTER COLUMN read_flag SET DEFAULT false;
COMMENT ON COLUMN notification.read_flag IS '是否已读。true=已读，false=未读';
