# 纸间书店

第一版在线书店：浏览图书、注册登录、购物车、下单扣库存、站内通知。前端只访问网关。通知使用同步 HTTP，不使用消息队列。

## 环境

- JDK 21
- Maven 3.9+
- Node.js 20+
- Docker 中的 PostgreSQL 16。容器名 `bookmall-postgres`，端口 `localhost:5432`，超级用户 `postgres` / `postgres`

数据库和种子数据如果还没建，在容器运行后执行：

```text
docker exec -i bookmall-postgres psql -U postgres -d postgres -f - < docs/sql/01-create-databases.sql
docker exec -i bookmall-postgres psql -U postgres -d bookmall_user -f - < docs/sql/02-bookmall-user.sql
docker exec -i bookmall-postgres psql -U postgres -d bookmall_book -f - < docs/sql/03-bookmall-book.sql
docker exec -i bookmall-postgres psql -U postgres -d bookmall_book -f - < docs/sql/06-seed-book.sql
docker exec -i bookmall-postgres psql -U postgres -d bookmall_order -f - < docs/sql/04-bookmall-order.sql
docker exec -i bookmall-postgres psql -U postgres -d bookmall_notification -f - < docs/sql/05-bookmall-notification.sql
```

Windows PowerShell 可以用：

```text
Get-Content docs/sql/01-create-databases.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d postgres
```

其余脚本把 `-d` 换成对应库名。

## 启动

先确认数据库容器在运行：`docker start bookmall-postgres`

再开五个终端：

```text
mvn -pl bookmall-user spring-boot:run
mvn -pl bookmall-catalog spring-boot:run
mvn -pl bookmall-order spring-boot:run
mvn -pl bookmall-notification spring-boot:run
mvn -pl bookmall-gateway spring-boot:run
```

前端：

```text
cd bookmall-web
npm install
npm run dev
```

浏览器打开 http://localhost:5173 。接口入口是 http://localhost:8080 。

如果本机 8080 已被占用，网关改用其它端口，前端用同一地址：

```text
mvn -pl bookmall-gateway spring-boot:run -Dspring-boot.run.arguments=--server.port=8090
```

```text
cd bookmall-web
$env:VITE_API_BASE="http://localhost:8090"
npm run dev
```

## 演示

1. 首页第一页能看到 10 本图书，一共 28 本，可以翻页和按分类筛选。
2. 注册 `demo_user` / `demo123456`，或使用已有账号登录。
3. 打开一本图书，加入购物车 2 本。
4. 购物车核对数量和金额，提交订单。
5. 我的订单和订单详情能看到这笔订单，状态显示「已创建」。
6. 消息中心能看到下单成功通知。
7. 该书库存减少 2。
8. 退出后访问购物车，会要求登录。

## 测试

数据库容器运行时：

```text
mvn test
```
