# Kubernetes 本地部署

清单用途：在 Kind 或 Docker Desktop Kubernetes 上跑第二版全套（Postgres / Redis / RabbitMQ + 五服务）。
网关 NodePort **30080**。`/internal/**` 仍不进网关路由。

## 1. 构建镜像

在仓库根目录：

```text
docker build -f deploy/docker/Dockerfile.service --build-arg MODULE=bookmall-user -t bookmall-user:1.0.0 .
docker build -f deploy/docker/Dockerfile.service --build-arg MODULE=bookmall-catalog -t bookmall-catalog:1.0.0 .
docker build -f deploy/docker/Dockerfile.service --build-arg MODULE=bookmall-order -t bookmall-order:1.0.0 .
docker build -f deploy/docker/Dockerfile.service --build-arg MODULE=bookmall-notification -t bookmall-notification:1.0.0 .
docker build -f deploy/docker/Dockerfile.service --build-arg MODULE=bookmall-gateway -t bookmall-gateway:1.0.0 .
```

Kind 需 `kind load docker-image ...`。

## 2. 应用清单

```text
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/configmap-secret.yaml
kubectl apply -f deploy/k8s/infra.yaml
# 等 Postgres Ready 后，按 docs/数据库设置.md 初始化（含 07-phase2-*.sql）
kubectl apply -f deploy/k8s/apps.yaml
```

## 3. 验证

```text
kubectl -n bookmall get pods
curl http://localhost:30080/api/categories
```

前端开发服务器把 `VITE_API_BASE` 指到 `http://localhost:30080`。
