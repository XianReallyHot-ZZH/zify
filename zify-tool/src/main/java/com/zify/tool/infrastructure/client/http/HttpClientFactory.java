package com.zify.tool.infrastructure.client.http;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 工具 HTTP 客户端工厂：按超时产出带超时的 {@link RestClient}（glm-docs/13 §4 / P2 §5.1）。
 * <p>
 * 集中 RestClient 构造（避免每调用在 HttpTool 内 new HttpClient）；按单次请求超时设 read timeout，
 * 按 connect 配置设 connect timeout。JdkClientHttpRequestFactory 支持虚拟线程中断取消。
 */
@Component
public class HttpClientFactory {

    public RestClient build(Duration connectTimeout, Duration readTimeout) {
        Duration connect = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
        Duration read = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connect).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(read);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
