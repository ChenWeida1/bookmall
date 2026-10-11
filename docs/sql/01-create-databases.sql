-- 以超级用户连接 postgres 库后执行：
--   psql -U postgres -h localhost -p 5432 -d postgres -f docs/sql/01-create-databases.sql
-- 口令仅用于本地开发，不要用于生产。
-- PostgreSQL 15 起，CREATE DATABASE 不能写在事务里；本文件按语句逐条执行。

CREATE ROLE bookmall_user_app LOGIN PASSWORD 'bookmall_user_dev';
CREATE ROLE bookmall_book_app LOGIN PASSWORD 'bookmall_book_dev';
CREATE ROLE bookmall_order_app LOGIN PASSWORD 'bookmall_order_dev';
CREATE ROLE bookmall_notification_app LOGIN PASSWORD 'bookmall_notification_dev';

CREATE DATABASE bookmall_user OWNER bookmall_user_app ENCODING 'UTF8';
CREATE DATABASE bookmall_book OWNER bookmall_book_app ENCODING 'UTF8';
CREATE DATABASE bookmall_order OWNER bookmall_order_app ENCODING 'UTF8';
CREATE DATABASE bookmall_notification OWNER bookmall_notification_app ENCODING 'UTF8';

REVOKE ALL ON DATABASE bookmall_user FROM PUBLIC;
REVOKE ALL ON DATABASE bookmall_book FROM PUBLIC;
REVOKE ALL ON DATABASE bookmall_order FROM PUBLIC;
REVOKE ALL ON DATABASE bookmall_notification FROM PUBLIC;

GRANT CONNECT ON DATABASE bookmall_user TO bookmall_user_app;
GRANT CONNECT ON DATABASE bookmall_book TO bookmall_book_app;
GRANT CONNECT ON DATABASE bookmall_order TO bookmall_order_app;
GRANT CONNECT ON DATABASE bookmall_notification TO bookmall_notification_app;
