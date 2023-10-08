package com.ambitious.iptvserver.config;

import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * 配置一个全局唯一的 okhttp 请求客户端
 * @author ambitious
 * @date 2023/7/8
 */
@Configuration
public class MyHttpClientConfig {

    @Bean
    public OkHttpClient httpClient() {
        return new OkHttpClient.Builder()
                .protocols(Collections.singletonList(Protocol.HTTP_1_1))
                .build();
    }
}
