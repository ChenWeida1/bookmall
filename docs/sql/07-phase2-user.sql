-- 第二版：用户角色与管理员种子。连接到 bookmall_user 后执行。
--   docker cp docs/sql/07-phase2-user.sql bookmall-postgres:/tmp/07-user.sql
--   docker exec bookmall-postgres psql -U postgres -d bookmall_user -f /tmp/07-user.sql

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS role smallint NOT NULL DEFAULT 1;

ALTER TABLE user_account DROP CONSTRAINT IF EXISTS ck_user_account_role;
ALTER TABLE user_account
    ADD CONSTRAINT ck_user_account_role CHECK (role IN (1, 2));

COMMENT ON COLUMN user_account.role IS '角色：1=读者，2=管理员';

-- 管理员 admin / admin123456（BCrypt，由 Spring BCryptPasswordEncoder 生成）
INSERT INTO user_account (username, password, nickname, role)
SELECT 'admin',
       '$2a$10$s8jna08JK8ftBqqRnwSN8uo.fJjhE17f7gzNaJjtVt1oXSYWfA7Rm',
       'Admin',
       2
WHERE NOT EXISTS (SELECT 1 FROM user_account WHERE username = 'admin');

UPDATE user_account
SET role = 2,
    password = '$2a$10$s8jna08JK8ftBqqRnwSN8uo.fJjhE17f7gzNaJjtVt1oXSYWfA7Rm',
    nickname = 'Admin'
WHERE username = 'admin';
