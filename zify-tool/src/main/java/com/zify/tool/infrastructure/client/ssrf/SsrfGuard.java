package com.zify.tool.infrastructure.client.ssrf;

import com.zify.tool.config.ToolProperties;
import com.zify.tool.infrastructure.exception.ToolNonRetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;

/**
 * SSRF 防护（黑名单模式，glm-docs/13 §8.1）。
 * <p>
 * 解析 URL → 取 host → DNS 解析 → 校验所有解析 IP 不在内网/保留地址黑名单。
 * enabled=false 直接放行；allowPrivate=true 时放行内网（内网部署）。
 * 校验时机：工具/MCP server 保存时（即时反馈，由 Service 转 BusinessException(TOOL_SSRF_BLOCKED)）
 * 与运行时（防配置后 IP 变更）都调。违例抛 ToolNonRetryableException（TOOL_SSRF_BLOCKED 语义）。
 * <p>
 * 一期基础防护（解析后比对 IP）；完整 DNS-rebinding 自定义 resolver 留二期。
 */
@Component
public class SsrfGuard {

    private static final Logger log = LoggerFactory.getLogger(SsrfGuard.class);

    /** 内网/保留地址黑名单（13 §8.1）：IPv4 七段 + IPv6 三段。 */
    private static final List<String> BLOCKED_CIDRS = List.of(
            "127.0.0.0/8",       // loopback
            "10.0.0.0/8",        // private A
            "172.16.0.0/12",     // private B
            "192.168.0.0/16",    // private C
            "169.254.0.0/16",    // link-local（含云元数据 169.254.169.254）
            "0.0.0.0/8",         // this network
            "100.64.0.0/10",     // CGN
            "::1/128",           // IPv6 loopback
            "fc00::/7",          // IPv6 ULA / private
            "fe80::/10"          // IPv6 link-local
    );

    private final ToolProperties properties;

    public SsrfGuard(ToolProperties properties) {
        this.properties = properties;
    }

    /**
     * 校验 URL，违例抛 {@link ToolNonRetryableException}。
     *
     * @param url       完整 URL（含 scheme）
     * @param toolId    审计上下文
     * @param toolName  审计上下文
     * @param scenario  审计场景（save / execute / mcp_handshake 等）
     */
    public void validate(String url, String toolId, String toolName, String scenario) {
        if (url == null || url.isBlank()) {
            throw new ToolNonRetryableException("empty url", toolId, toolName, scenario);
        }
        if (!properties.getSecurity().getSsrf().isEnabled()) {
            return;
        }
        String host = extractHost(url, toolId, toolName, scenario);
        if (host == null || host.isBlank()) {
            throw new ToolNonRetryableException("url has no host: " + mask(url),
                    toolId, toolName, scenario);
        }

        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            // 无法解析：视为不可达，按 SSRF 违例拒绝（防用未解析名绕过）
            throw new ToolNonRetryableException("unable to resolve host: " + host,
                    toolId, toolName, scenario, e);
        }

        if (!properties.getSecurity().getSsrf().isAllowPrivate()) {
            for (InetAddress addr : resolved) {
                InetAddress normalized = normalize(addr);
                if (isBlocked(normalized)) {
                    log.warn("event=ssrf_blocked scenario={} host={} ip={} url={}",
                            scenario, host, normalized.getHostAddress(), mask(url));
                    throw new ToolNonRetryableException(
                            "blocked by SSRF guard (private/reserved address): " + host,
                            toolId, toolName, scenario);
                }
            }
        }
    }

    /** 从 URL 提取 host（去端口）。 */
    private String extractHost(String url, String toolId, String toolName, String scenario) {
        try {
            String u = url.trim();
            if (!u.contains("://")) {
                u = "http://" + u;
            }
            URI uri = new URI(u);
            return uri.getHost();
        } catch (URISyntaxException e) {
            throw new ToolNonRetryableException("invalid url: " + mask(url),
                    toolId, toolName, scenario, e);
        }
    }

    /** 归一化：IPv4-mapped IPv6（::ffff:a.b.c.d）转 IPv4。 */
    private InetAddress normalize(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        if (bytes.length == 16 && isV4Mapped(bytes)) {
            byte[] v4 = new byte[4];
            System.arraycopy(bytes, 12, v4, 0, 4);
            try {
                return InetAddress.getByAddress(v4);
            } catch (UnknownHostException e) {
                return addr;
            }
        }
        return addr;
    }

    private boolean isV4Mapped(byte[] b) {
        return b[0] == 0 && b[1] == 0 && b[2] == 0 && b[3] == 0
                && b[4] == 0 && b[5] == 0 && b[6] == 0 && b[7] == 0
                && b[8] == 0 && b[9] == 0 && b[10] == (byte) 0xff && b[11] == (byte) 0xff;
    }

    /** 命中黑名单任一段即拒绝。 */
    private boolean isBlocked(InetAddress addr) {
        byte[] ipBytes = addr.getAddress();
        BigInteger ipInt = new BigInteger(1, ipBytes);
        int totalBits = ipBytes.length * 8;
        for (String cidr : BLOCKED_CIDRS) {
            if (inRange(ipInt, totalBits, cidr)) {
                return true;
            }
        }
        return false;
    }

    private boolean inRange(BigInteger ipInt, int ipTotalBits, String cidr) {
        int slash = cidr.indexOf('/');
        String baseStr = cidr.substring(0, slash);
        int prefix = Integer.parseInt(cidr.substring(slash + 1));
        InetAddress base;
        try {
            base = InetAddress.getByName(baseStr);
        } catch (UnknownHostException e) {
            return false;
        }
        byte[] baseBytes = base.getAddress();
        if (baseBytes.length * 8 != ipTotalBits) {
            // 不同地址族（v4 vs v6）不匹配
            return false;
        }
        BigInteger baseInt = new BigInteger(1, baseBytes);
        // /N → 高 N 位置 1 的掩码：(2^N - 1) << (totalBits - N)
        BigInteger mask = prefix == 0
                ? BigInteger.ZERO
                : BigInteger.ONE.shiftLeft(prefix).subtract(BigInteger.ONE)
                        .shiftLeft(ipTotalBits - prefix);
        return ipInt.and(mask).equals(baseInt.and(mask));
    }

    /** 日志脱敏：只保留 scheme + host，去掉 path/query。 */
    private String mask(String url) {
        try {
            String u = url.contains("://") ? url : "http://" + url;
            URI uri = new URI(u);
            String host = uri.getHost();
            return host != null ? uri.getScheme() + "://" + host : "invalid-url";
        } catch (URISyntaxException e) {
            return "invalid-url";
        }
    }
}
