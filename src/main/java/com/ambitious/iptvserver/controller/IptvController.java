package com.ambitious.iptvserver.controller;

import cn.hutool.core.util.StrUtil;
import com.ambitious.iptvserver.config.IptvConfig;
import com.ambitious.iptvserver.entity.ServerInfo;
import com.ambitious.iptvserver.job.service.ServerTest;
import kotlin.Pair;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.view.RedirectView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @author ambitious
 * @date 2023/10/8
 */
@Controller
@Slf4j
public class IptvController {

    @Resource
    private IptvConfig iptvConfig;
    @Resource(name = "ffmpegServerTest")
    private ServerTest serverTest;
    @Resource
    private OkHttpClient httpClient;

    @GetMapping("/iptv")
    public String iptv(@RequestParam String type) {
        if (StrUtil.isEmpty(type)) {
            throw new RuntimeException("type 不能为空");
        }
        // 2 尝试获取直播源列表
        List<ServerInfo> servers = IptvConfig.getServers(type);
        if (servers == null || servers.isEmpty()) {
            throw new RuntimeException("获取不到可用的直播源");
        }
        // 3 获取一个可用的直播源
        String server = getAvailableServer(servers, type);
        if (StrUtil.isEmpty(server)) {
            throw new RuntimeException("获取不到可用直播源");
        }
        // 4 执行代理
        if (IptvConfig.checkNeedProxy(server)) {
            return "forward:/iptv/proxy?url=" + server;
        }
        return "redirect:" + server;
    }

    @GetMapping("/iptv/proxy")
    public ResponseEntity<byte[]> proxyIptv(@RequestParam String url) throws IOException {
        if (StrUtil.isEmpty(url)) {
            throw new RuntimeException("url 为空");
        }
        // 1 代理请求，获取 m3u8
        Map<String, String> proxyHeaders = IptvConfig.getProxyHeaders(url);
        Request request = new Request.Builder()
                .url(url)
                .headers(Headers.of(proxyHeaders))
                .get()
                .build();
        try (Response resp = httpClient.newCall(request).execute()) {
            int code = resp.code();
            if (code != HttpStatus.OK.value()) {
                throw new RuntimeException("请求失败");
            }
            if (resp.body() == null) {
                throw new RuntimeException("请求失败");
            }
            return ResponseEntity
                    .status(code)
                    .headers(headers -> {
                        for (Pair<? extends String, ? extends String> pair : resp.headers()) {
                            headers.add(pair.getFirst(), pair.getSecond());
                        }
                    })
                    .body(resp.body().bytes());
        }
    }

    /**
     * 从直播源列表中找一个可用的地址
     * 思路：向直播源地址发送请求，如果响应码是 200，就认为它是可用的
     * @param servers 直播源列表
     * @return 可用的地址，如果都不可用，返回空
     */
    private String getAvailableServer(List<ServerInfo> servers, String tvName) {
        for (ServerInfo serverInfo : servers) {
            String server = serverInfo.getUrl();
            // 如果是需要代理的直播源，默认可用
            if (IptvConfig.checkNeedProxy(server) || serverTest.test(server)) {
                return server;
            } else {
                log.error("电视台：{}，直播源：{}，不可用", tvName, server);
            }
        }
        log.error("电视台：{} 找不到可用直播源，请尝试更换直播源", tvName);
        return null;
    }

    @GetMapping("/iptv-refresh")
    @ResponseBody
    public String refresh() {
        boolean success = iptvConfig.refreshServers();
        return "直播源更新" + (success ? "成功(*^▽^*)" : "失败o(╥﹏╥)o，请稍后再试");
    }
}
