package com.ambitious.iptvserver.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置一个全局唯一的 okhttp 请求客户端
 * @author ambitious
 * @date 2023/7/8
 */
@Configuration
public class MyHttpClientConfig {

    @Bean
    public OkHttpClient httpClient() {
        return new OkHttpClient();
    }
}
