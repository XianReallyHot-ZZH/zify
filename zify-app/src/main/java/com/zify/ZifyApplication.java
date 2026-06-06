package com.zify;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;

/**
 * Zify 应用启动类
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceInitializationAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
})
public class ZifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZifyApplication.class, args);
    }
}
