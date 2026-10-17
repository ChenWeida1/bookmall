-- 第六期：book 增加可空 isbn。连接到 bookmall_book 后执行，可重复执行。
--   Get-Content docs/sql/11-phase6-book.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_book

ALTER TABLE book ADD COLUMN IF NOT EXISTS isbn varchar(32);

COMMENT ON COLUMN book.isbn IS 'ISBN，可空，最长 32';
