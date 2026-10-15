-- 第五期：已有库可重复执行。
-- 增加 Seata 协调者库、客服库，以及图书库、订单库的 undo_log。
-- 从 postgres 库执行：
--   Get-Content docs/sql/10-phase5.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d postgres -f -

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', 'bookmall_seata_app', 'bookmall_seata_dev')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bookmall_seata_app')\gexec

SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', 'bookmall_assistant_app', 'bookmall_assistant_dev')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'bookmall_assistant_app')\gexec

SELECT 'CREATE DATABASE bookmall_seata OWNER bookmall_seata_app ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bookmall_seata')\gexec

SELECT 'CREATE DATABASE bookmall_assistant OWNER bookmall_assistant_app ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'bookmall_assistant')\gexec

REVOKE ALL ON DATABASE bookmall_seata FROM PUBLIC;
REVOKE ALL ON DATABASE bookmall_assistant FROM PUBLIC;
GRANT CONNECT ON DATABASE bookmall_seata TO bookmall_seata_app;
GRANT CONNECT ON DATABASE bookmall_assistant TO bookmall_assistant_app;

\connect bookmall_book

CREATE TABLE IF NOT EXISTS undo_log (
    id            SERIAL       NOT NULL,
    branch_id     BIGINT       NOT NULL,
    xid           VARCHAR(128) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info BYTEA        NOT NULL,
    log_status    INT          NOT NULL,
    log_created   TIMESTAMP(0) NOT NULL,
    log_modified  TIMESTAMP(0) NOT NULL,
    CONSTRAINT pk_undo_log PRIMARY KEY (id),
    CONSTRAINT ux_undo_log UNIQUE (xid, branch_id)
);
CREATE INDEX IF NOT EXISTS ix_log_created ON undo_log (log_created);
ALTER TABLE undo_log OWNER TO bookmall_book_app;
ALTER SEQUENCE IF EXISTS undo_log_id_seq OWNER TO bookmall_book_app;
COMMENT ON TABLE undo_log IS 'Seata AT 回滚日志。由图书服务在全局事务中写入。';

\connect bookmall_order

CREATE TABLE IF NOT EXISTS undo_log (
    id            SERIAL       NOT NULL,
    branch_id     BIGINT       NOT NULL,
    xid           VARCHAR(128) NOT NULL,
    context       VARCHAR(128) NOT NULL,
    rollback_info BYTEA        NOT NULL,
    log_status    INT          NOT NULL,
    log_created   TIMESTAMP(0) NOT NULL,
    log_modified  TIMESTAMP(0) NOT NULL,
    CONSTRAINT pk_undo_log PRIMARY KEY (id),
    CONSTRAINT ux_undo_log UNIQUE (xid, branch_id)
);
CREATE INDEX IF NOT EXISTS ix_log_created ON undo_log (log_created);
ALTER TABLE undo_log OWNER TO bookmall_order_app;
ALTER SEQUENCE IF EXISTS undo_log_id_seq OWNER TO bookmall_order_app;
COMMENT ON TABLE undo_log IS 'Seata AT 回滚日志。由订单服务在全局事务中写入。';

\connect bookmall_seata

CREATE TABLE IF NOT EXISTS global_table (
    xid                       VARCHAR(128) NOT NULL,
    transaction_id            BIGINT,
    status                    SMALLINT     NOT NULL,
    application_id            VARCHAR(32),
    transaction_service_group VARCHAR(32),
    transaction_name          VARCHAR(128),
    timeout                   INT,
    begin_time                BIGINT,
    application_data          VARCHAR(2000),
    gmt_create                TIMESTAMP(0),
    gmt_modified              TIMESTAMP(0),
    CONSTRAINT pk_global_table PRIMARY KEY (xid)
);
CREATE INDEX IF NOT EXISTS idx_status_gmt_modified ON global_table (status, gmt_modified);
CREATE INDEX IF NOT EXISTS idx_transaction_id ON global_table (transaction_id);

CREATE TABLE IF NOT EXISTS branch_table (
    branch_id         BIGINT       NOT NULL,
    xid               VARCHAR(128) NOT NULL,
    transaction_id    BIGINT,
    resource_group_id VARCHAR(32),
    resource_id       VARCHAR(256),
    branch_type       VARCHAR(8),
    status            SMALLINT,
    client_id         VARCHAR(64),
    application_data  VARCHAR(2000),
    gmt_create        TIMESTAMP(6),
    gmt_modified      TIMESTAMP(6),
    CONSTRAINT pk_branch_table PRIMARY KEY (branch_id)
);
CREATE INDEX IF NOT EXISTS idx_xid ON branch_table (xid);

CREATE TABLE IF NOT EXISTS lock_table (
    row_key        VARCHAR(128) NOT NULL,
    xid            VARCHAR(128),
    transaction_id BIGINT,
    branch_id      BIGINT       NOT NULL,
    resource_id    VARCHAR(256),
    table_name     VARCHAR(32),
    pk             VARCHAR(128),
    status         SMALLINT     NOT NULL DEFAULT 0,
    gmt_create     TIMESTAMP(0),
    gmt_modified   TIMESTAMP(0),
    CONSTRAINT pk_lock_table PRIMARY KEY (row_key)
);
CREATE INDEX IF NOT EXISTS idx_branch_id ON lock_table (branch_id);

CREATE TABLE IF NOT EXISTS distributed_lock (
    lock_key   VARCHAR(20)  NOT NULL,
    lock_value VARCHAR(20)  NOT NULL,
    expire     BIGINT,
    CONSTRAINT pk_distributed_lock_table PRIMARY KEY (lock_key)
);

INSERT INTO distributed_lock (lock_key, lock_value, expire)
SELECT 'AsyncCommitting', ' ', 0
WHERE NOT EXISTS (SELECT 1 FROM distributed_lock WHERE lock_key = 'AsyncCommitting');
INSERT INTO distributed_lock (lock_key, lock_value, expire)
SELECT 'RetryCommitting', ' ', 0
WHERE NOT EXISTS (SELECT 1 FROM distributed_lock WHERE lock_key = 'RetryCommitting');
INSERT INTO distributed_lock (lock_key, lock_value, expire)
SELECT 'RetryRollbacking', ' ', 0
WHERE NOT EXISTS (SELECT 1 FROM distributed_lock WHERE lock_key = 'RetryRollbacking');
INSERT INTO distributed_lock (lock_key, lock_value, expire)
SELECT 'TxTimeoutCheck', ' ', 0
WHERE NOT EXISTS (SELECT 1 FROM distributed_lock WHERE lock_key = 'TxTimeoutCheck');

ALTER TABLE global_table OWNER TO bookmall_seata_app;
ALTER TABLE branch_table OWNER TO bookmall_seata_app;
ALTER TABLE lock_table OWNER TO bookmall_seata_app;
ALTER TABLE distributed_lock OWNER TO bookmall_seata_app;
GRANT USAGE, CREATE ON SCHEMA public TO bookmall_seata_app;

\connect bookmall_assistant

CREATE TABLE IF NOT EXISTS assistant_message (
    id         bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    user_id    bigint      NOT NULL,
    role       varchar(16) NOT NULL,
    content    text        NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_assistant_message_role CHECK (role IN ('user', 'assistant')),
    CONSTRAINT ck_assistant_message_content_len CHECK (char_length(content) BETWEEN 1 AND 16000)
);
CREATE INDEX IF NOT EXISTS idx_assistant_message_user ON assistant_message (user_id, created_at, id);
COMMENT ON TABLE assistant_message IS '客服对话。一个用户一条时间线。';
COMMENT ON COLUMN assistant_message.role IS 'user 或 assistant';
ALTER TABLE assistant_message OWNER TO bookmall_assistant_app;
GRANT USAGE, CREATE ON SCHEMA public TO bookmall_assistant_app;
