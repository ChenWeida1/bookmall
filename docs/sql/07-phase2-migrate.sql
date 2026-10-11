-- 第二版增量脚本。在已有一期库上执行，可重复执行时部分语句会因已存在而失败，需按环境处理。
-- Docker 示例：
--   Get-Content docs/sql/07-phase2-migrate.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_user
-- 再对 bookmall_order、bookmall_notification 执行对应段落（本文件按库分段，请分三次执行或整文件在对应库粘贴）。

-- ========== bookmall_user ==========
ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS role smallint NOT NULL DEFAULT 1;
ALTER TABLE user_account DROP CONSTRAINT IF EXISTS ck_user_account_role;
ALTER TABLE user_account
    ADD CONSTRAINT ck_user_account_role CHECK (role IN (1, 2));
COMMENT ON COLUMN user_account.role IS '角色：1=读者，2=管理员';

-- 种子管理员：密码 admin123456 的 BCrypt（启动后若无该用户则由应用或下方插入）
-- 若已存在同名用户则跳过；口令哈希在应用首次启动时可改密。此处用占位，实际由 seed 脚本写入。

-- ========== bookmall_order（单独连库执行下面）==========
-- ALTER TABLE order_main DROP CONSTRAINT IF EXISTS ck_order_main_status;
-- ALTER TABLE order_main ADD CONSTRAINT ck_order_main_status CHECK (status IN (1, 2));
-- COMMENT ON COLUMN order_main.status IS '订单状态：1=已创建，2=已取消';

-- ========== bookmall_notification（单独连库执行下面）==========
-- ALTER TABLE notification ADD COLUMN IF NOT EXISTS biz_key varchar(64);
-- CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_biz_key ON notification (biz_key) WHERE biz_key IS NOT NULL;
-- COMMENT ON COLUMN notification.biz_key IS '业务幂等键，例如 ORDER_CREATED:订单号、ORDER_CANCELLED:订单号';
