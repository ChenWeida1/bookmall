-- 第二版：通知业务幂等键。连接到 bookmall_notification 后执行。
--   Get-Content docs/sql/07-phase2-notification.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_notification

ALTER TABLE notification
    ADD COLUMN IF NOT EXISTS biz_key varchar(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_biz_key
    ON notification (biz_key)
    WHERE biz_key IS NOT NULL;

COMMENT ON COLUMN notification.biz_key IS '业务幂等键，例如 ORDER_CREATED:订单号、ORDER_CANCELLED:订单号';
