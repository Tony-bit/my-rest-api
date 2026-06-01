# 阿里云部署指南

## 部署架构

```
┌─────────────────────────────────────────────────────────┐
│                    阿里云 ECS                            │
│                                                         │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │
│  │   Docker    │    │   Docker    │    │   Docker    │  │
│  │  Container  │    │  Container  │    │  Container  │  │
│  │             │    │             │    │             │  │
│  │  Frontend   │───▶│  Backend    │───▶│   MySQL     │  │
│  │  (Nginx)    │    │  (Spring)   │    │  (远程)      │  │
│  │   :80       │    │   :8091    │    │             │  │
│  └─────────────┘    └─────────────┘    └─────────────┘  │
│        ▲                  ▲                             │
│        │                  │                             │
│  ┌─────┴──────────────────┴─────┐                       │
│  │      Docker Network          │                       │
│  └──────────────────────────────┘                       │
│                                                         │
│  端口: 80 (HTTP) / 443 (HTTPS, 可选)                    │
└─────────────────────────────────────────────────────────┘
```

## 方式一：一键部署 (推荐) - 后端 + 前端

### 1. 安装 Docker

```bash
# 更新系统
sudo yum update -y

# 安装 Docker
sudo yum install -y docker.io

# 启动 Docker
sudo systemctl start docker
sudo systemctl enable docker

# 安装 Docker Compose v2
sudo curl -L "https://github.com/docker/compose/releases/download/v2.24.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 验证安装
docker-compose version
```

### 2. 上传项目

```bash
# 在本地打包项目
cd /path/to/my-rest-api
scp -r . user@your-server:/opt/my-rest-api/

# 或使用 Git
cd /opt
git clone https://github.com/yourusername/my-rest-api.git
cd my-rest-api
```

### 3. 配置环境变量

```bash
cd /opt/my-rest-api
cp .env.example .env
vim .env
```

编辑 `.env` 文件：

```env
# 数据库配置 (你已有的远程 MySQL)
DB_HOST=111.228.9.124
DB_PORT=3306
DB_NAME=trading_plan
DB_USERNAME=navicat_user
DB_PASSWORD=Panzer1234@

# Tushare Token (可选)
TUSHARE_TOKEN=e054d234d3479bb5c6e7e1146c361d511a7cd9c8bb6de49d37b385c0
```

### 4. 构建并启动

```bash
# 一键构建并启动所有服务 (后端 + 前端)
docker-compose -f docker-compose.all-in-one.yml up -d --build

# 查看服务状态
docker-compose -f docker-compose.all-in-one.yml ps

# 查看日志
docker-compose -f docker-compose.all-in-one.yml logs -f
```

### 5. 验证部署

```bash
# 1. 检查容器状态
docker ps

# 2. 访问前端 (验证 Nginx)
curl http://localhost/health
# 输出: OK

# 3. 访问后端 API
curl http://localhost/api/api/v1/health
# 或通过前端容器代理
curl http://localhost/api/actuator/health

# 4. 打开浏览器访问
http://你的服务器IP
```

---

## 方式二：仅部署后端

如果只需要后端 API：

```bash
# 使用基础配置
docker-compose -f docker-compose.yml up -d --build

# 访问 API
curl http://localhost:8091/actuator/health
```

---

## 方式三：使用阿里云容器镜像服务

### 1. 在服务器构建并推送镜像

```bash
# 登录阿里云容器镜像服务
docker login --username=你的用户名 registry.cn-hangzhou.aliyuncs.com

# 构建后端镜像
docker build -t my-rest-api-backend:latest .

# 构建前端镜像
cd frontend
docker build -t my-rest-api-frontend:latest .
cd ..

# 打标签
docker tag my-rest-api-backend:latest registry.cn-hangzhou.aliyuncs.com/你的命名空间/my-rest-api-backend:latest
docker tag my-rest-api-frontend:latest registry.cn-hangzhou.aliyuncs.com/你的命名空间/my-rest-api-frontend:latest

# 推送镜像
docker push registry.cn-hangzhou.aliyuncs.com/你的命名空间/my-rest-api-backend:latest
docker push registry.cn-hangzhou.aliyuncs.com/你的命名空间/my-rest-api-frontend:latest
```

---

## 阿里云安全组配置

在阿里云 ECS 安全组中开放以下端口：

| 端口 | 协议 | 用途 |
|------|------|------|
| 80 | TCP | HTTP 前端访问 |
| 443 | TCP | HTTPS (可选) |

---

## 配置开机自启

```bash
# 创建 systemd 服务文件
sudo vim /etc/systemd/system/my-rest-api.service

# 写入内容:
[Unit]
Description=My REST API (Backend + Frontend)
Requires=docker.service
After=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
WorkingDirectory=/opt/my-rest-api
ExecStart=/usr/local/bin/docker-compose -f docker-compose.all-in-one.yml up -d
ExecStop=/usr/local/bin/docker-compose -f docker-compose.all-in-one.yml down
ExecReload=/usr/local/bin/docker-compose -f docker-compose.all-in-one.yml restart
RemainAfterExit=yes

[Install]
WantedBy=multi-user.target

# 启用服务
sudo systemctl daemon-reload
sudo systemctl enable my-rest-api.service
sudo systemctl start my-rest-api.service

# 管理命令
sudo systemctl status my-rest-api.service
sudo systemctl restart my-rest-api.service
```

---

## 常用运维命令

```bash
# 进入容器调试
docker exec -it my-api-backend /bin/sh
docker exec -it my-api-frontend /bin/sh

# 查看资源使用
docker stats

# 查看日志
docker-compose logs -f backend    # 只看后端日志
docker-compose logs -f frontend   # 只看前端日志

# 重启服务
docker-compose -f docker-compose.all-in-one.yml restart

# 更新部署
cd /opt/my-rest-api
git pull
docker-compose -f docker-compose.all-in-one.yml up -d --build

# 停止服务
docker-compose -f docker-compose.all-in-one.yml down

# 完全清理 (包括镜像)
docker-compose -f docker-compose.all-in-one.yml down --rmi all
```

---

## 数据备份

```bash
# 创建备份脚本
cat > /opt/backup.sh << 'EOF'
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR=/opt/backups
mkdir -p $BACKUP_DIR

# 备份数据库
mysqldump -h $DB_HOST -P $DB_PORT -u $DB_USERNAME -p$DB_PASSWORD $DB_NAME > $BACKUP_DIR/trading_plan_$DATE.sql

# 保留最近 30 天的备份
find $BACKUP_DIR -name "*.sql" -mtime +30 -delete

echo "[$(date)] Backup completed: trading_plan_$DATE.sql"
EOF

chmod +x /opt/backup.sh

# 添加定时任务
crontab -e
# 每天凌晨 2 点备份
0 2 * * * /opt/backup.sh >> /var/log/backup.log 2>&1
```

---

## HTTPS 配置 (可选)

### 使用 Let's Encrypt 免费证书

```bash
# 安装 Certbot
sudo yum install -y certbot python3-certbot-nginx

# 申请证书 (需要域名已解析到服务器)
sudo certbot --nginx -d your-domain.com

# 自动续期
sudo certbot renew --dry-run
```

### 配置 Nginx HTTPS

取消注释 `nginx.conf` 中的 HTTPS server 块，并配置证书路径。

---

## 目录结构

```
my-rest-api/
├── docker-compose.all-in-one.yml  # 一体化部署配置
├── docker-compose.yml              # 仅后端部署
├── Dockerfile.optimized           # 后端优化 Dockerfile
├── .env.example                   # 环境变量模板
├── frontend/
│   ├── Dockerfile                 # 前端构建配置
│   └── nginx.conf                 # 前端 Nginx 配置
├── DEPLOY.md                      # 本文档
└── ...
```
