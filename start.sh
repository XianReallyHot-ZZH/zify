#!/usr/bin/env bash
set -euo pipefail

# ── Zify 本地开发一键启动脚本 ──────────────────────────────────────
# 用法：bash start.sh
# 流程：检查环境 → 构建 → 启动后端 → 健康检查 → 启动前端

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$ROOT_DIR/zify.pid"
LOG_FILE="$ROOT_DIR/logs/zify.log"
JAR="$ROOT_DIR/zify-app/target/zify-app-0.1.0-SNAPSHOT.jar"

BACKEND_PORT=8080
FRONTEND_PORT=5173
HEALTH_URL="http://localhost:${BACKEND_PORT}/api/health"
HEALTH_TIMEOUT=90

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
die()   { error "$*"; exit 1; }
step()  { echo -e "\n${CYAN}━━━ $* ━━━${NC}"; }

# ── 清理函数：Ctrl+C 时杀掉后端进程 ────────────────────────────────
cleanup() {
  echo ""
  if [[ -f "$PID_FILE" ]]; then
    pid=$(cat "$PID_FILE")
    if kill -0 "$pid" 2>/dev/null; then
      info "正在停止后端进程（PID=${pid}）..."
      kill "$pid" 2>/dev/null || true
      # 等待进程退出，最多 10 秒
      for _ in $(seq 1 10); do
        kill -0 "$pid" 2>/dev/null || break
        sleep 1
      done
      # 仍未退出则强制 kill
      if kill -0 "$pid" 2>/dev/null; then
        kill -9 "$pid" 2>/dev/null || true
      fi
    fi
    rm -f "$PID_FILE"
  fi
  info "已退出"
  exit 0
}
trap cleanup INT TERM

# ── 第 1 步：前置检查 ──────────────────────────────────────────────
step "前置检查"

# Java
command -v java &>/dev/null || die "未找到 java 命令，请安装 JDK 21+"
java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
[[ "$java_version" -ge 21 ]] 2>/dev/null || die "需要 Java 21+，当前版本：$java_version"
info "Java $java_version ✓"

# Maven
command -v mvn &>/dev/null || die "未找到 mvn 命令，请安装 Maven 3.9+"
info "Maven $(mvn --version 2>/dev/null | head -1 | awk '{print $3}') ✓"

# MySQL
if command -v mysqladmin &>/dev/null; then
  if mysqladmin ping -h localhost -P 3306 -u root --password=123456 --silent 2>/dev/null; then
    info "MySQL localhost:3306 ✓"
  else
    warn "MySQL localhost:3306 不可用，后端启动可能失败"
  fi
else
  warn "未找到 mysqladmin，跳过 MySQL 检查"
fi

# Redis
if command -v redis-cli &>/dev/null; then
  if redis-cli -h localhost -p 6379 ping &>/dev/null; then
    info "Redis localhost:6379 ✓"
  else
    warn "Redis localhost:6379 不可用，后端启动可能失败"
  fi
else
  warn "未找到 redis-cli，跳过 Redis 检查"
fi

# curl（健康检查需要）
command -v curl &>/dev/null || die "未找到 curl 命令，健康检查需要 curl"

# ── 第 2 步：构建后端 ──────────────────────────────────────────────
step "构建后端"

info "执行 mvn package -DskipTests ..."
cd "$ROOT_DIR"
if ! mvn package -DskipTests -q; then
  die "Maven 构建失败，请检查编译错误"
fi
info "构建成功 ✓"

[[ -f "$JAR" ]] || die "构建完成但找不到 JAR：$JAR"

# ── 第 3 步：后台启动后端 ──────────────────────────────────────────
step "启动后端"

# 如果已有进程在运行
if [[ -f "$PID_FILE" ]]; then
  old_pid=$(cat "$PID_FILE")
  if kill -0 "$old_pid" 2>/dev/null; then
    warn "后端已在运行（PID=${old_pid}），先停止..."
    kill "$old_pid" 2>/dev/null || true
    sleep 3
    if kill -0 "$old_pid" 2>/dev/null; then
      kill -9 "$old_pid" 2>/dev/null || true
      sleep 1
    fi
  fi
  rm -f "$PID_FILE"
fi

mkdir -p "$(dirname "$LOG_FILE")"

info "JAR  : $JAR"
info "日志 : $LOG_FILE"
info "端口 : $BACKEND_PORT"

nohup java -jar "$JAR" >> "$LOG_FILE" 2>&1 &
PID=$!
echo "$PID" > "$PID_FILE"
info "后端进程 PID=$PID"

# ── 第 4 步：轮询健康检查 ─────────────────────────────────────────
step "等待后端就绪"

info "健康检查：$HEALTH_URL（最多 ${HEALTH_TIMEOUT}s）..."
elapsed=0
until curl -sf "$HEALTH_URL" &>/dev/null; do
  # 检查进程是否还活着
  if ! kill -0 "$PID" 2>/dev/null; then
    error "后端进程已退出，最后日志："
    echo "───────────────────────────────────────"
    tail -30 "$LOG_FILE" >&2
    echo "───────────────────────────────────────"
    rm -f "$PID_FILE"
    die "后端启动失败"
  fi

  sleep 2
  elapsed=$((elapsed + 2))
  if [[ $elapsed -ge $HEALTH_TIMEOUT ]]; then
    error "后端启动超时（${HEALTH_TIMEOUT}s），最后日志："
    echo "───────────────────────────────────────"
    tail -30 "$LOG_FILE" >&2
    echo "───────────────────────────────────────"
    kill "$PID" 2>/dev/null || true
    rm -f "$PID_FILE"
    die "后端未能在 ${HEALTH_TIMEOUT}s 内就绪"
  fi
  printf "."
done
echo ""

info "✅ 后端就绪：http://localhost:${BACKEND_PORT}/api/health"

# ── 第 5 步：启动前端开发服务器 ────────────────────────────────────
step "启动前端"

cd "$ROOT_DIR/zify-web"

if [[ ! -d "node_modules" ]]; then
  info "安装前端依赖..."
  npm install || die "npm install 失败"
fi

info "前端地址：http://localhost:${FRONTEND_PORT}"
info "API 代理 ：/api → http://localhost:${BACKEND_PORT}"
info ""
info "按 Ctrl+C 退出（会自动停止后端进程）"
info "───────────────────────────────────────────"

npm run dev || true

# npm run dev 退出后清理
cleanup
