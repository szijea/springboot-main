# 部署指南（Git + Docker）

## 1. 推送代码到 Git 仓库
确保本地已初始化 Git：
```bash
git init
git add .
git commit -m "feat: initial multi-tenant docker deployment"
# 添加远程（示例：GitHub）
git remote add origin https://github.com/<your-account>/<repo-name>.git
git push -u origin main
```

若已有仓库只需：
```bash
git add .
git commit -m "chore: update docker deployment"
git push
```

## 2. 服务器准备（Linux）
安装 Docker 与 Compose：
```bash
# Docker 安装（Ubuntu 示例）
curl -fsSL https://get.docker.com | bash
sudo usermod -aG docker $USER
# 重新登陆 shell 后验证
docker version

# 安装 docker compose v2（若未自动包含）
DOCKER_CONFIG=${DOCKER_CONFIG:-$HOME/.docker}
mkdir -p $DOCKER_CONFIG/cli-plugins
curl -SL https://github.com/docker/compose/releases/download/v2.27.0/docker-compose-linux-x86_64 -o $DOCKER_CONFIG/cli-plugins/docker-compose
chmod +x $DOCKER_CONFIG/cli-plugins/docker-compose
docker compose version
```

## 3. 拉取代码到服务器
```bash
# 建议在 /opt 或 /srv 下
cd /opt
sudo git clone https://github.com/<your-account>/<repo-name>.git pharmacy-system
cd pharmacy-system
```

## 4. 构建后端 Jar（如果不使用 CI）
服务器需要 JDK 17 + Maven（可直接本地构建后上传 jar）：
```bash
./mvnw -q clean package
ls target/pharmacy-system-*.jar
```

## 5. Docker 构建与启动
```bash
docker compose build --no-cache
docker compose up -d

# 查看容器状态
docker compose ps
# 查看应用日志
docker logs -f pharmacy-app
```

## 6. 验证多租户接口
```bash
curl -H "X-Shop-Id: bht" http://<server-ip>:8080/api/members
curl -H "X-Shop-Id: wx"  http://<server-ip>:8080/api/suppliers
curl -H "X-Shop-Id: rzt" http://<server-ip>:8080/api/inventory
```

## 7. 常见问题
| 现象 | 原因 | 解决 |
|------|------|------|
| 访问 8080 失败 | 防火墙未放行 | `ufw allow 8080` 或安全组开放端口 |
| MySQL 连接错误 | 初始化顺序/脚本未运行 | `docker compose down -v && docker compose up -d` 重建 |
| 多租户数据未隔离 | 未传 X-Shop-Id | 请求头加上 `-H "X-Shop-Id: wx"` |
| 415 Unsupported Media Type | JSON 非标准或缺少 Content-Type | 使用 `-H "Content-Type: application/json"` 并双引号字段 |
| 表缺失 (stock_in) | 初始化脚本只建基础结构，后续补全失败 | 查看应用日志 `[SchemaInit]`，检查权限/连接 |

## 8. 升级发布流程
```bash
git pull
./mvnw -q clean package
docker compose build --no-cache
docker compose up -d --force-recreate
```

## 9. 备份策略（可选）
```bash
# 备份 MySQL 数据（每日定时）
docker exec pharmacy-mysql mysqldump -uroot -p123456 --databases bht wx rzt_db > backup-$(date +%F).sql
```

## 10. 生产建议
- 使用独立的 MySQL 卷或云数据库，替换 docker-compose 中 db 服务。
- 打开应用健康检查（集成 `spring-boot-starter-actuator`）。
- 配置 Nginx 反向代理启用 HTTPS。
- 使用 CI/CD（GitHub Actions、Jenkins）自动构建并推送镜像到镜像仓库（Docker Hub / ACR）。

## 11. 推镜像示例（可选）
```bash
docker login
# 改为你的仓库名
docker tag pharmacy-app <your-dockerhub-username>/pharmacy-app:1.0.0
docker push <your-dockerhub-username>/pharmacy-app:1.0.0
# 服务器上直接拉取
# docker pull <your-dockerhub-username>/pharmacy-app:1.0.0
```

## 12. 验证前端页面
浏览器访问：http://<server-ip>:8080/index.html （或其他页面）
确保请求头带租户（前端需在登录后将 X-Shop-Id 注入 fetch / axios）。

---
如需接入 CI、Nginx、HTTPS 或监控（Prometheus/Grafana），可在后续扩展。

