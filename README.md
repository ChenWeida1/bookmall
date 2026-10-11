# 纸间书店

第二版在线书店：浏览图书、注册登录、购物车、下单扣库存、订单取消、管理端上下架、站内通知。  
前端只访问网关。通知经 **RabbitMQ** 异步入库；图书列表用 **Redis** 短缓存。

## 环境

- JDK 21、Maven 3.9+、Node.js 20+
- Docker：PostgreSQL 16、Redis 7、RabbitMQ 3（管理台 15672）

## 数据库初始化

```text
docker compose up -d postgres redis rabbitmq
```

建库与种子（PowerShell）：

```text
Get-Content docs/sql/01-create-databases.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d postgres
Get-Content docs/sql/02-bookmall-user.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_user
Get-Content docs/sql/03-bookmall-book.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_book
Get-Content docs/sql/06-seed-book.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_book
Get-Content docs/sql/04-bookmall-order.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_order
Get-Content docs/sql/05-bookmall-notification.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_notification
Get-Content docs/sql/07-phase2-user.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_user
Get-Content docs/sql/07-phase2-order.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_order
Get-Content docs/sql/07-phase2-notification.sql -Raw | docker exec -i bookmall-postgres psql -U postgres -d bookmall_notification
```

已有一期库时只需执行三个 `07-phase2-*.sql`。管理员种子：`admin` / `admin123456`。

## 本地启动（Maven）

依赖容器已起后：

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

浏览器 http://localhost:5173 ，接口 http://localhost:8080 。

8080 被占用时：

```text
mvn -pl bookmall-gateway spring-boot:run "-Dspring-boot.run.arguments=--server.port=8090"
```

```text
cd bookmall-web
$env:VITE_API_BASE="http://localhost:8090"
npm run dev
```

## Docker Compose 全套后端

```text
docker compose up -d --build
```

网关仍为 http://localhost:8080 。前端继续 `npm run dev` 即可联调。

## Kubernetes（Kind / Docker Desktop）

见 [deploy/k8s/README.md](deploy/k8s/README.md)。网关 NodePort：`http://localhost:30080`。

## 演示路径

1. 首页浏览分类与图书（第二次列表请求可命中 Redis）。
2. 注册读者，或登录 `admin` / `admin123456`。
3. 加购 → 整车下单 → 订单「已创建」，库存减少；消息中心出现下单通知（MQ，略延迟可接受）。
4. 取消订单 → 状态「已取消」，库存加回，可出现取消通知。
5. 管理员打开「管理」：下架某书后，在售列表不再出现；读者不可加购下架书。
6. 游客访问购物车会被要求登录；网关访问 `/internal/**` 为 404。

## 测试

```text
mvn test
```

## 文档

- [需求规格说明书](docs/需求规格说明书.md)
- [接口文档](docs/接口文档.md)
- [数据库设置](docs/数据库设置.md)
- [页面与交互说明](docs/prototype/页面与交互说明.md)
