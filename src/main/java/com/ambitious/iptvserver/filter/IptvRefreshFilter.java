package com.ambitious.iptvserver.filter;

import com.ambitious.iptvserver.config.IptvConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author ambitious
 * @date 2023/9/8
 */
@Component
@Slf4j
public class IptvRefreshFilter implements GlobalFilter, Ordered {

    @Resource
    private IptvConfig iptvConfig;

    /**
     * 通过特定的路径来刷新直播源数据
     */
    public static final String VALID_PATH = "/iptv-refresh";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestPath = exchange.getRequest().getPath().value();
        if (!VALID_PATH.equals(requestPath)) {
            // 继续执行过滤器链
            return chain.filter(exchange);
        }
        boolean success = iptvConfig.refreshServers();
        String responseText = "直播源更新" + (success ? "成功(*^▽^*)" : "失败o(╥﹏╥)o，请稍后再试");
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.OK);
        response.getHeaders().setContentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(response.bufferFactory().wrap(responseText.getBytes(StandardCharsets.UTF_8))));
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
