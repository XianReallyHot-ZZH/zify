package com.zify.tool.domain.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zify.tool.infrastructure.client.mcp.McpClientFactory;
import com.zify.tool.infrastructure.client.ssrf.SsrfGuard;
import com.zify.tool.infrastructure.entity.McpServerEntity;
import com.zify.tool.infrastructure.mapper.McpServerMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * MCP 连接生命周期管理（程序化 McpSyncClient，glm-docs/13 §9.3 / P2 §七）。
 * <p>
 * 常驻保活：启动连已配置的 enabled server；新增即时连；删除/禁用断连；断连定时重连；
 * 连接健康度（ONLINE/OFFLINE/ERROR）写 mcp_server（短事务仅 DB 写）。
 * <p>
 * 工具发现见 {@code discoverTools}（P2 §八，连接成功后调 listTools）。
 */
@Component
public class McpConnectionManager implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(McpConnectionManager.class);
    private static final String STATUS_ONLINE = "ONLINE";
    private static final String STATUS_OFFLINE = "OFFLINE";
    private static final String STATUS_ERROR = "ERROR";

    private final McpServerMapper mapper;
    private final McpClientFactory clientFactory;
    private final SsrfGuard ssrfGuard;
    private final ExecutorService toolExecutor;

    /** serverId → client（已连接）。 */
    private final ConcurrentHashMap<String, McpSyncClient> clients = new ConcurrentHashMap<>();

    public McpConnectionManager(McpServerMapper mapper, McpClientFactory clientFactory, SsrfGuard ssrfGuard,
                                @Qualifier("toolExecutor") ExecutorService toolExecutor) {
        this.mapper = mapper;
        this.clientFactory = clientFactory;
        this.ssrfGuard = ssrfGuard;
        this.toolExecutor = toolExecutor;
    }

    /**
     * 连接（握手）：SSRF 校验 → 建连 → initialize → 成功置 ONLINE，失败置 ERROR。
     *
     * @return 是否连接成功（供 McpServerService 决定是否发现工具）
     */
    public boolean connect(McpServerEntity server) {
        if (server == null || server.getEnabled() == null || server.getEnabled() != 1) {
            return false;
        }
        String serverId = server.getId();
        String name = server.getName();
        try {
            ssrfGuard.validate(server.getBaseUrl(), serverId, name, "mcp_handshake");
            closeExisting(serverId);
            McpSyncClient client = clientFactory.build(server);
            client.initialize(); // 握手（mcp-handshake 超时由 factory 设）
            clients.put(serverId, client);
            updateStatus(serverId, STATUS_ONLINE, null, LocalDateTime.now());
            log.info("event=mcp_connect server={} status=ONLINE", name);
            return true;
        } catch (Exception e) {
            closeExisting(serverId);
            updateStatus(serverId, STATUS_ERROR, brief(e), null);
            log.warn("event=mcp_connect server={} status=ERROR error={}", name, brief(e));
            return false;
        }
    }

    /** 断开连接并置 OFFLINE。 */
    public void disconnect(String serverId) {
        if (serverId == null) {
            return;
        }
        closeExisting(serverId);
        updateStatus(serverId, STATUS_OFFLINE, null, null);
        log.info("event=mcp_disconnect serverId={} status=OFFLINE", serverId);
    }

    /** 取已连接 client；不存在/未初始化返回空。 */
    public McpSyncClient getClient(String serverId) {
        McpSyncClient client = clients.get(serverId);
        if (client == null) {
            return null;
        }
        try {
            if (!client.isInitialized()) {
                return null;
            }
            return client;
        } catch (Exception e) {
            return null;
        }
    }

    /** server 是否在线（供 listAvailableTools 过滤）。 */
    public boolean isOnline(String serverId) {
        return getClient(serverId) != null;
    }

    /**
     * 应用启动：异步连接所有 enabled=1 的 server（不阻塞启动）。
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            List<McpServerEntity> servers = mapper.selectList(new LambdaQueryWrapper<McpServerEntity>()
                    .eq(McpServerEntity::getEnabled, 1));
            for (McpServerEntity server : servers) {
                toolExecutor.submit(() -> connect(server));
            }
            log.info("MCP startup: scheduled {} server connection(s)", servers.size());
        } catch (Exception e) {
            log.warn("MCP startup connect scan failed: {}", e.getMessage());
        }
    }

    /**
     * 每 30s 重连非 ONLINE 的 enabled server（首次延迟 30s 避免与启动重叠）。
     */
    @Scheduled(fixedDelayString = "30000", initialDelayString = "30000")
    public void reconnectDownServers() {
        try {
            List<McpServerEntity> down = mapper.selectList(new LambdaQueryWrapper<McpServerEntity>()
                    .eq(McpServerEntity::getEnabled, 1)
                    .ne(McpServerEntity::getStatus, STATUS_ONLINE));
            for (McpServerEntity server : down) {
                toolExecutor.submit(() -> connect(server));
            }
        } catch (Exception e) {
            log.warn("MCP reconnect scan failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void closeAll() {
        for (String serverId : clients.keySet()) {
            McpSyncClient client = clients.remove(serverId);
            if (client == null) {
                continue;
            }
            try {
                client.closeGracefully();
            } catch (Exception ignored) {
                // 关闭异常忽略
            }
        }
    }

    private void closeExisting(String serverId) {
        McpSyncClient existing = clients.remove(serverId);
        if (existing != null) {
            try {
                existing.close();
            } catch (Exception ignored) {
                // 关闭异常忽略
            }
        }
    }

    private void updateStatus(String serverId, String status, String message, LocalDateTime lastConnectedAt) {
        LambdaUpdateWrapper<McpServerEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(McpServerEntity::getId, serverId);
        wrapper.set(McpServerEntity::getStatus, status);
        wrapper.set(McpServerEntity::getStatusMessage, message);
        if (lastConnectedAt != null) {
            wrapper.set(McpServerEntity::getLastConnectedAt, lastConnectedAt);
        }
        mapper.update(null, wrapper);
    }

    private static String brief(Throwable e) {
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : (msg.length() > 240 ? msg.substring(0, 240) : msg);
    }
}
