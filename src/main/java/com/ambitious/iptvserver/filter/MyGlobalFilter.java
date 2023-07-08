package com.ambitious.iptvserver.filter;

import cn.hutool.core.util.StrUtil;
import com.ambitious.iptvserver.config.IptvConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * 全局过滤器
 * @author ambitious
 * @date 2023/7/8
 */
@Component
@Slf4j
public class MyGlobalFilter implements GlobalFilter, Ordered {

    @Resource
    private OkHttpClient httpClient;
    private String tvName;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse response = exchange.getResponse();
        // 1 获取用户要请求的电视台名称
        String type = exchange.getRequest().getQueryParams().getFirst("type");
        if (StrUtil.isEmpty(type)) {
            return parseError(response);
        }
        // 2 尝试获取直播源列表
        List<String> servers = IptvConfig.getServers(type);
        if (servers == null || servers.isEmpty()) {
            return parseError(response);
        }
        this.tvName = type;
        // 3 获取一个可用的直播源
        String server = getAvailableServer(servers);
        if (StrUtil.isEmpty(server)) {
            return parseError(response);
        }
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(server));
        return response.setComplete();
    }

    /**
     * 从直播源列表中找一个可用的地址
     * 思路：向直播源地址发送请求，如果响应码是 200，就认为它是可用的
     * @param servers 直播源列表
     * @return 可用的地址，如果都不可用，返回空
     */
    private String getAvailableServer(List<String> servers) {
        for (String server : servers) {
            Request request = new Request.Builder()
                    .url(server)
                    .get()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.code() == 200) {
                    return server;
                }
            } catch (IOException e) {
                log.error("电视台：{}，直播源：{}，不可用", this.tvName, server);
            }
        }
        log.error("电视台：{} 找不到可用直播源，请尝试更换直播源", this.tvName);
        return null;
    }

    /**
     * 直播源转换异常
     * @param response 请求响应对象
     * @return 请求结束对象
     */
    private Mono<Void> parseError(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.NOT_FOUND);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
