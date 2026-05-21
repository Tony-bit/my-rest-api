#!/bin/bash

# ================================================
# 部署脚本 - 适用于阿里云 ECS Ubuntu
# 用法: bash deploy.sh
# ================================================

set -e

# ---------- 配置区 ----------
APP_NAME="my-rest-api"
APP_DIR="/opt/${APP_NAME}"
DB_NAME="trading_plan"
DB_USER="appuser"
DB_PASSWORD="your_secure_password"      # 修改为你的密码
TUSHARE_TOKEN=""                        # 填入你的 tushare token
PROFILE="prod"
JAR_PORT=8080
# -----------------------------

echo "===== 开始部署 ${APP_NAME} ====="

# 1. 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "[ERROR] Docker 未安装，请先运行: sudo apt install -y docker.io"
    exit 1
fi

# 2. 检查 MySQL
if ! command -v mysql &> /dev/null; then
    echo "[ERROR] MySQL 未安装，请先运行: sudo apt install -y mysql-server"
    exit 1
fi

# 3. 创建应用目录
echo "[1/6] 创建应用目录..."
sudo mkdir -p ${APP_DIR}
sudo chown $(whoami):$(whoami) ${APP_DIR}

# 4. 复制项目文件（如果还没在服务器上，从这里开始复制）
echo "[2/6] 复制项目文件到 ${APP_DIR}..."
echo "      请先将本地项目上传到服务器: scp -r /path/to/my-rest-api/* user@host:${APP_DIR}/"

# 5. 构建 Docker 镜像
echo "[3/6] 构建 Docker 镜像..."
cd ${APP_DIR}
docker build -t ${APP_NAME} .

# 6. 停止并删除旧容器
echo "[4/6] 停止旧容器..."
docker stop ${APP_NAME} 2>/dev/null || true
docker rm ${APP_NAME} 2>/dev/null || true

# 7. 启动新容器
echo "[5/6] 启动容器..."
docker run -d \
    --name ${APP_NAME} \
    -p ${JAR_PORT}:${JAR_PORT} \
    -e SPRING_PROFILES_ACTIVE=${PROFILE} \
    -e TUSHARE_TOKEN=${TUSHARE_TOKEN} \
    --restart unless-stopped \
    ${APP_NAME}

# 8. 等待启动并验证
echo "[6/6] 验证服务..."
sleep 10
HEALTH=$(curl -s http://localhost:${JAR_PORT}/actuator/health 2>/dev/null || echo '{"status": "DOWN"}')
echo "健康检查: ${HEALTH}"

if echo "${HEALTH}" | grep -q '"status":"UP"'; then
    echo ""
    echo "===== 部署成功! ====="
    echo "访问地址: http://你的服务器IP:${JAR_PORT}/actuator/health"
else
    echo ""
    echo "===== 启动可能有问题，请检查日志 ====="
    echo "日志命令: docker logs -f ${APP_NAME}"
fi
