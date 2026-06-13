#!/bin/bash
# ============================================================
# 稿件管理系统 - 自动化部署脚本
# 用法：./deploy.sh [环境: dev|staging|prod]
# 默认环境：dev
# ============================================================
set -euo pipefail

# --- 配置变量 ---
ENV="${1:-dev}"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WAR_NAME="gaojiaoguanli.war"
TOMCAT_HOME="${TOMCAT_HOME:-/opt/tomcat}"
DEPLOY_DIR="${TOMCAT_HOME}/webapps"
CONFIG_BASE="/opt/config"
UPLOAD_DIR="/data/uploads/gaojiaoguanli"
CONTEXT_XML="${TOMCAT_HOME}/conf/Catalina/localhost/gaojiaoguanli.xml"
BACKUP_DIR="${TOMCAT_HOME}/backup"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

echo "============================================"
echo "  稿件管理系统部署"
echo "  环境: ${ENV}"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================"

# --- 1. 构建 ---
echo "[1/7] Maven 构建..."
cd "${PROJECT_DIR}"
mvn clean package -DskipTests -q
if [ ! -f "target/${WAR_NAME}" ]; then
    echo "ERROR: 构建失败，未生成 ${WAR_NAME}"
    exit 1
fi
echo "  OK: target/${WAR_NAME}"

# --- 2. 准备目录 ---
echo "[2/7] 准备目录..."
mkdir -p "${UPLOAD_DIR}"
mkdir -p "${CONFIG_BASE}/${ENV}"
mkdir -p "${BACKUP_DIR}"
mkdir -p "$(dirname ${CONTEXT_XML})"

# --- 3. 停止 Tomcat ---
echo "[3/7] 停止 Tomcat..."
if [ -f "${TOMCAT_HOME}/bin/shutdown.sh" ]; then
    "${TOMCAT_HOME}/bin/shutdown.sh" || true
    sleep 5
    # 确认进程已退出
    if pgrep -f "catalina.base=${TOMCAT_HOME}" > /dev/null 2>&1; then
        echo "  WARNING: Tomcat 未正常停止，等待 10 秒后强制终止..."
        sleep 10
        pkill -9 -f "catalina.base=${TOMCAT_HOME}" || true
    fi
fi

# --- 4. 备份当前 WAR ---
echo "[4/7] 备份当前版本..."
if [ -f "${DEPLOY_DIR}/${WAR_NAME}" ]; then
    cp "${DEPLOY_DIR}/${WAR_NAME}" "${BACKUP_DIR}/${WAR_NAME}_${TIMESTAMP}"
    echo "  OK: ${BACKUP_DIR}/${WAR_NAME}_${TIMESTAMP}"
else
    echo "  SKIP: 无现有 WAR 需要备份（首次部署）"
fi

# --- 5. 部署新 WAR ---
echo "[5/7] 部署新版本..."
# 清理旧的展开目录（如存在）
rm -rf "${DEPLOY_DIR}/gaojiaoguanli"
cp "target/${WAR_NAME}" "${DEPLOY_DIR}/${WAR_NAME}"
echo "  OK: ${DEPLOY_DIR}/${WAR_NAME}"

# --- 6. 部署 context.xml（上传目录映射） ---
echo "[6/7] 部署 Tomcat context.xml..."
if [ -f "${PROJECT_DIR}/deploy/context.xml" ]; then
    cp "${PROJECT_DIR}/deploy/context.xml" "${CONTEXT_XML}"
    echo "  OK: ${CONTEXT_XML}"
else
    echo "  WARNING: deploy/context.xml 不存在，跳过"
fi

# --- 7. 首次部署：迁移历史上传文件 ---
if [ "${ENV}" = "prod" ] && [ ! -f "${UPLOAD_DIR}/.initialized" ]; then
    echo "[7/7] 首次部署：迁移历史上传文件..."
    if [ -d "${PROJECT_DIR}/src/main/webapp/upload" ]; then
        cp -rn "${PROJECT_DIR}/src/main/webapp/upload/"* "${UPLOAD_DIR}/" 2>/dev/null || true
        touch "${UPLOAD_DIR}/.initialized"
        echo "  OK: 历史文件已迁移到 ${UPLOAD_DIR}"
    else
        echo "  SKIP: 无历史上传文件"
        touch "${UPLOAD_DIR}/.initialized"
    fi
else
    echo "[7/7] SKIP: 非首次部署或非生产环境"
fi

# --- 8. 启动 Tomcat ---
echo "[8/8] 启动 Tomcat..."
export CATALINA_OPTS="-Dconfig.dir=${CONFIG_BASE}/${ENV}"
"${TOMCAT_HOME}/bin/startup.sh"

# 等待启动完成
echo "  等待 Tomcat 启动..."
MAX_WAIT=120
WAITED=0
while [ ${WAITED} -lt ${MAX_WAIT} ]; do
    if curl -sf "http://localhost:8080/gaojiaoguanli/jsp/login.jsp" > /dev/null 2>&1; then
        echo "  OK: Tomcat 已启动 (${WAITED}s)"
        break
    fi
    sleep 3
    WAITED=$((WAITED + 3))
done
if [ ${WAITED} -ge ${MAX_WAIT} ]; then
    echo "  WARNING: 等待超时 (${MAX_WAIT}s)，请手动检查"
    echo "  查看日志：tail -f ${TOMCAT_HOME}/logs/catalina.out"
fi

echo ""
echo "============================================"
echo "  部署完成！"
echo "  环境: ${ENV}"
echo "  WAR:  ${DEPLOY_DIR}/${WAR_NAME}"
echo "  配置: ${CONFIG_BASE}/${ENV}/config.properties"
echo "  上传: ${UPLOAD_DIR}"
echo "============================================"
echo ""
echo "验证清单："
echo "  1. 访问 http://localhost:8080/gaojiaoguanli/jsp/login.jsp"
echo "  2. 以管理员/作者/专家三种角色登录"
echo "  3. 测试稿件上传和下载"
echo "  4. 测试 UEditor 富文本中插入图片"
echo "  5. 检查盲审全流程"
