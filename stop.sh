#!/usr/bin/env bash
set -euo pipefail

# Stop Zify backend and frontend gracefully
# Usage: bash stop.sh [--force]

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$ROOT_DIR/zify.pid"

GRACEFUL_TIMEOUT=10

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ── Stop backend via PID file ─────────────────────────────────────
stop_backend() {
  if [[ ! -f "$PID_FILE" ]]; then
    warn "PID file not found: $PID_FILE"
    return
  fi

  pid=$(cat "$PID_FILE")

  if ! kill -0 "$pid" 2>/dev/null; then
    info "Backend process $pid already stopped"
    rm -f "$PID_FILE"
    return
  fi

  # SIGTERM
  info "Sending SIGTERM to backend (PID=$pid) ..."
  kill "$pid" 2>/dev/null || true

  # Wait for graceful shutdown
  elapsed=0
  while kill -0 "$pid" 2>/dev/null; do
    if [[ $elapsed -ge $GRACEFUL_TIMEOUT ]]; then
      warn "Backend did not stop within ${GRACEFUL_TIMEOUT}s, sending SIGKILL ..."
      kill -9 "$pid" 2>/dev/null || true
      sleep 1
      break
    fi
    sleep 1
    elapsed=$((elapsed + 1))
    printf "."
  done
  echo ""

  if kill -0 "$pid" 2>/dev/null; then
    error "Backend process $pid could not be stopped"
    exit 1
  fi

  info "Backend stopped (PID=$pid)"
  rm -f "$PID_FILE"
}

# ── Stop frontend dev server (vite on port 5173) ──────────────────
stop_frontend() {
  local found=0

  # Find node processes serving vite on FRONTEND_PORT
  while IFS= read -r pid; do
    if kill -0 "$pid" 2>/dev/null; then
      found=1
      info "Sending SIGTERM to frontend (PID=$pid) ..."
      kill "$pid" 2>/dev/null || true

      elapsed=0
      while kill -0 "$pid" 2>/dev/null; do
        if [[ $elapsed -ge $GRACEFUL_TIMEOUT ]]; then
          warn "Frontend did not stop within ${GRACEFUL_TIMEOUT}s, sending SIGKILL ..."
          kill -9 "$pid" 2>/dev/null || true
          sleep 1
          break
        fi
        sleep 1
        elapsed=$((elapsed + 1))
      done

      if kill -0 "$pid" 2>/dev/null; then
        warn "Frontend process $pid could not be stopped"
      else
        info "Frontend stopped (PID=$pid)"
      fi
    fi
  done < <(lsof -ti :5173 2>/dev/null || true)

  if [[ $found -eq 0 ]]; then
    # Fallback: find by command line
    while IFS= read -r pid; do
      if kill -0 "$pid" 2>/dev/null; then
        found=1
        info "Sending SIGTERM to frontend (PID=$pid) ..."
        kill "$pid" 2>/dev/null || true
        sleep 2
        if kill -0 "$pid" 2>/dev/null; then
          kill -9 "$pid" 2>/dev/null || true
        fi
        info "Frontend stopped (PID=$pid)"
      fi
    done < <(pgrep -f "vite" 2>/dev/null || true)
  fi

  if [[ $found -eq 0 ]]; then
    info "No frontend process found"
  fi
}

# ── Main ───────────────────────────────────────────────────────────
echo "Stopping Zify ..."
stop_backend
stop_frontend
info "All stopped"
