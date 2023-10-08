package com.ambitious.iptvserver.filter;

import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import com.ambitious.iptvserver.config.IptvConfig;
import com.ambitious.iptvserver.entity.ServerInfo;
import com.ambitious.iptvserver.job.service.ServerTest;
import kotlin.Pair;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.annotation.Resource;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * 过滤 iptv 源
 * @author ambitious
 * @date 2023/7/8
 */
@Component
@Slf4j
public class IptvFilter implements GlobalFilter, Ordered {

    @Resource(name = "ffmpegServerTest")
    private ServerTest serverTest;
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
        List<ServerInfo> servers = IptvConfig.getServers(type);
        if (servers == null || servers.isEmpty()) {
            return parseError(response);
        }
        this.tvName = type;
        // 3 获取一个可用的直播源
        String server = getAvailableServer(servers);
        if (StrUtil.isEmpty(server)) {
            return parseError(response);
        }
        // 4 执行代理
        if (IptvConfig.checkNeedProxy(server)) {
            return proxyUrl(response, server);
        }
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(server));
        return response.setComplete();
    }

    private Mono<Void> proxyUrl(ServerWebExchange exchange, String url) {
        WebClient client = WebClient.create();
        ServerHttpResponse response = exchange.getResponse();
        return client.method(HttpMethod.GET)
                .uri(URI.create(url))
                .headers(headers -> {
                    Map<String, String> proxyHeaders = IptvConfig.getProxyHeaders(url);
                    for (String key : proxyHeaders.keySet()) {
                        headers.add(key, proxyHeaders.get(key));
                    }
                })
                .exchangeToMono(resp -> {
                    if (resp.statusCode().is2xxSuccessful()) {
                        HttpHeaders respHeaders = resp.headers().asHttpHeaders();
                        HttpStatus statusCode = resp.statusCode();
                        response.setStatusCode(statusCode);
                        response.getHeaders().addAll(respHeaders);
                        // return resp.bodyToMono(DataBuffer.class)
                        //         .flatMap(dataBuffer -> response.writeAndFlushWith(Mono.just(Mono.just(dataBuffer))));
                        return response.writeAndFlushWith(Mono.just(resp.body(BodyExtractors.toDataBuffers())));
                    } else {
                        response.setStatusCode(HttpStatus.FOUND);
                        response.getHeaders().setLocation(URI.create(url));
                        return response.setComplete();
                    }
                });
    }

    /**
     * 代理直播源
     * @param response 当前请求的响应对象
     * @param url 要代理的直播源地址
     */
    private Mono<Void> proxyUrl(ServerHttpResponse response, String url) {
        // 1 代理请求，获取 m3u8
        HttpRequest request = new HttpRequest(UrlBuilder.of(url));
        request.addHeaders(IptvConfig.getProxyHeaders(url));
        request.method(Method.GET);
        try (HttpResponse resp = request.execute()) {
            // 2 响应请求
            if (resp.getStatus() == cn.hutool.http.HttpStatus.HTTP_OK) {
                response.setStatusCode(HttpStatus.resolve(resp.getStatus()));
                for (String key : resp.headers().keySet()) {
                    if (StrUtil.isEmpty(key)) {
                        continue;
                    }
                    for (String value : resp.headers().get(key)) {
                        response.getHeaders().add(key, value);
                    }
                }
                if (resp.body() == null) {
                    throw new RuntimeException("proxy request body is empty");
                }
                return response.writeWith(Mono.just(response.bufferFactory().wrap(resp.bodyBytes())));
            }
            throw new RuntimeException("请求失败: " + resp.getStatus() + " " + resp.body());
        } catch (Exception e) {
            log.error("代理 url 失败：{}", e.getMessage());
            response.setStatusCode(HttpStatus.FOUND);
            response.getHeaders().setLocation(URI.create(url));
            return response.setComplete();
        }
    }

    /**
     * 从直播源列表中找一个可用的地址
     * 思路：向直播源地址发送请求，如果响应码是 200，就认为它是可用的
     * @param servers 直播源列表
     * @return 可用的地址，如果都不可用，返回空
     */
    private String getAvailableServer(List<ServerInfo> servers) {
        for (ServerInfo serverInfo : servers) {
            String server = serverInfo.getUrl();
            // 如果是需要代理的直播源，默认可用
            if (IptvConfig.checkNeedProxy(server) || serverTest.test(server)) {
                return server;
            } else {
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
