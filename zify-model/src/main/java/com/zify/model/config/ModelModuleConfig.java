package com.zify.model.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * zify-model 模块配置
 */
@Configuration
public class ModelModuleConfig {

    /**
     * 模型健康测试专用 RestClient（连接超时 5s，读取超时 15s）
     */
    @Bean
    public RestClient modelTestRestClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        return builder.requestFactory(requestFactory).build();
    }
}
