# Zify Makefile
# Usage: make <target>
# Targets: start, stop, restart, build, clean, package

.PHONY: start stop restart build backend frontend clean package help

# ── Config ────────────────────────────────────────────────────────

VERSION      := 0.1.0-SNAPSHOT
BACKEND_JAR  := zify-app/target/zify-app-$(VERSION).jar
PID_FILE     := zify.pid
LOG_FILE     := logs/zify.log
HEALTH_URL   := http://localhost:8080/api/health
DIST_NAME    := zify-$(VERSION)

# ── Start / Stop / Restart ────────────────────────────────────────

start: ## Start backend + frontend for local dev
	@bash start.sh

stop: ## Stop backend and frontend gracefully
	@bash stop.sh

restart: stop ## Restart (stop then start)
	@sleep 2
	@bash start.sh

# ── Build ─────────────────────────────────────────────────────────

build: backend frontend ## Build backend JAR + frontend dist

backend: ## Build backend with Maven
	@echo "[build] mvn package -DskipTests ..."
	@mvn package -DskipTests -q
	@echo "[build] Backend OK: $(BACKEND_JAR)"

frontend: ## Build frontend with Vite
	@echo "[build] npm run build ..."
	@cd zify-web && npm run build
	@echo "[build] Frontend OK: zify-web/dist/"

# ── Clean ─────────────────────────────────────────────────────────

clean: ## Remove all build artifacts
	@echo "[clean] Removing Maven target/ ..."
	@find . -name "target" -type d -exec rm -rf {} + 2>/dev/null || true
	@echo "[clean] Removing zify-web/dist/ ..."
	@rm -rf zify-web/dist
	@echo "[clean] Removing logs/ ..."
	@rm -rf logs
	@rm -f $(PID_FILE)
	@echo "[clean] Done"

# ── Package ───────────────────────────────────────────────────────

package: build ## Package distributable tar.gz
	@echo "[package] Building $(DIST_NAME).tar.gz ..."
	@rm -rf /tmp/$(DIST_NAME)
	@mkdir -p /tmp/$(DIST_NAME)/bin
	@mkdir -p /tmp/$(DIST_NAME)/lib
	@mkdir -p /tmp/$(DIST_NAME)/web
	@mkdir -p /tmp/$(DIST_NAME)/logs
	@cp $(BACKEND_JAR) /tmp/$(DIST_NAME)/lib/
	@cp -r zify-web/dist/* /tmp/$(DIST_NAME)/web/
	@cp start.sh stop.sh /tmp/$(DIST_NAME)/bin/
	@chmod +x /tmp/$(DIST_NAME)/bin/*.sh
	@cd /tmp && tar czf $(DIST_NAME).tar.gz $(DIST_NAME)
	@mv /tmp/$(DIST_NAME).tar.gz .
	@rm -rf /tmp/$(DIST_NAME)
	@echo "[package] Done: $(DIST_NAME).tar.gz"
	@tar tzf $(DIST_NAME).tar.gz | head -10
	@echo "  ..."

# ── Help ──────────────────────────────────────────────────────────

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "} {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'
