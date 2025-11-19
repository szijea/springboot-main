# Pharmacy System Docker 部署指南

## 1. 前置条件
- 已安装 Docker Desktop (Windows) 并处于 Linux 容器模式
- 已安装 Git (可选)
- 可访问公网镜像仓库；若网络受限请配置镜像加速器

## 2. 目录结构关键点
- `Dockerfile` 多阶段构建：第一阶段编译 Jar，第二阶段运行
- `docker-compose.yml` 定义 `db` (MySQL 8.0) 与 `app`
- 初始化脚本：`docker/mysql/init/` 目录下的 `init-multitenant-simple.sql`
- 可选 profile: `application-docker.yaml` 使用容器内主机名 `db`

## 3. 构建与启动
```bash
# 0) 可先本地编译（可选）
./mvnw.cmd -DskipTests package

# 1) 构建镜像（首次可能较慢）
docker compose build

# 2) 启动后台服务
docker compose up -d

# 3) 查看运行状态
docker ps
docker logs -f pharmacy-app
```

## 4. 验证
```bash
# 应用是否存活
curl http://localhost:8080/api/orders/ping

# 多租户测试
curl -H "X-Shop-Id: wx" http://localhost:8080/api/members/debug-all
curl -H "X-Shop-Id: bht" http://localhost:8080/api/members/debug-all
```

## 5. 常见问题
| 问题 | 可能原因 | 解决 |
|------|----------|------|
| `failed to fetch anonymous token` 拉取基础镜像失败 | 网络/防火墙/IPv6 解析问题 | 配置加速器或离线导入镜像 |
| MySQL 表缺失 | 初始化脚本未执行或卷已存在 | `docker compose down` + 删除卷后重启 |
| 415 Unsupported Media Type | 前端未 JSON.stringify | 已在 `common.js` 自动序列化 |
| 多租户数据源不加载 | 环境���量不匹配 | 确认 compose 中 SPRING_TENANTS_* 变量 |

## 6. 镜像加速示例
Docker Desktop Settings -> Docker Engine:
```json
{
  "registry-mirrors": ["https://docker.m.daocloud.io"]
}
```
保存后重启。然后重试：
```bash
docker pull eclipse-temurin:17-jdk
docker pull mysql:8.0
```

## 7. 离线镜像导入
在有网络的机器：
```bash
docker pull eclipse-temurin:17-jdk
docker pull mysql:8.0
docker save -o temurin17.tar eclipse-temurin:17-jdk
docker save -o mysql8.tar mysql:8.0
```
拷贝到目标机器：
```bash
docker load -i temurin17.tar
docker load -i mysql8.tar
```
然后重新构建：
```bash
docker compose build --no-cache
docker compose up -d
```

## 8. 停止与清理
```bash
docker compose down  # 停止容器
# 删除持久化卷（谨慎，会清空数据库）
docker volume rm springboot-main_mysql-data || true
```

## 9. 下一步优化建议
- 使用 Flyway 正式迁移脚本取代自动补列逻辑
- 增加 Prometheus/JVM Metrics 监控
- 构建阶段开启测试（移除 -DskipTests）
- 使用 distroless 运行镜像进一步减小体积

## 10. 生产注意
- 修改 root 密码与各租户数据库的专用账号
- 使用反向代理 (Nginx) + HTTPS
- 打开 MySQL 慢查询日志，定期分析性能

---
如需进一步自动化（CI/CD、推送镜像仓库），可继续扩展。
