package com.ambitious.iptvserver.config;

import com.ambitious.iptvserver.entity.ServerInfo;
import com.ambitious.iptvserver.util.CastUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 处理 Iptv 相关配置
 * @author ambitious
 * @date 2023/7/8
 */
@Configuration
@ConfigurationProperties(prefix = "iptv")
@Data
@Slf4j
public class IptvConfig implements InitializingBean {

    /**
     * 远程的 iptv 服务器列表配置链接
     */
    private String serverConfigUrl;
    @Resource
    private OkHttpClient httpClient;
    private static final Map<String, List<ServerInfo>> SERVERS_MAP = Maps.newHashMap();

    /**
     * 依据直播源评分重新排序
     * @param tvKey 直播源 key
     */
    public static void reSort(String tvKey) {
        List<ServerInfo> servers = getServers(tvKey);
        if (servers == null || servers.isEmpty()) {
            return;
        }
        servers.sort((s1, s2) -> s2.getSuccessRate().compareTo(s1.getSuccessRate()));
    }

    /**
     * 获取当前服务器中配置的所有直播源 key
     * @return 所有 key
     */
    public static List<String> getAllTypes() {
        return Lists.newArrayList(SERVERS_MAP.keySet());
    }

    /**
     * 根据电视台名称从 SERVERS_MAP 中获取对应电视台的直播源列表
     * @param tvName 电视台名称
     * @return 直播源列表，如果不存在该电视台的配置，就返回空
     */
    public static List<ServerInfo> getServers(String tvName) {
        return SERVERS_MAP.get(tvName);
    }

    /**
     * 读取远程的 yml 配置文件，将数据源预处理成一个 HashMap
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        Map<String, Object> rawMap = readConfigAsMap();
        formatServersMap(rawMap);
    }

    /**
     * 将 rawMap 转化为 serversMap
     * @param rawMap 通过 SnakeYml 读取到的原始 map
     */
    private void formatServersMap(Map<String, Object> rawMap) {
        String errorMsgPrefix = "远程配置文件读取转换异常 ==> ";
        if (rawMap == null || rawMap.isEmpty()) {
            throw new RuntimeException(errorMsgPrefix + "远程配置为空");
        }
        for (String tvName : rawMap.keySet()) {
            List<String> servers = CastUtils.cast(rawMap.get(tvName));
            if (servers == null || servers.isEmpty()) {
                throw new RuntimeException(errorMsgPrefix + "电视台 " + tvName + " 的直播源数据列表为空");
            }
            SERVERS_MAP.put(tvName, servers.stream().map(ServerInfo::new).collect(Collectors.toList()));
        }
        log.info("远程配置文件转换成功");
        log.info(SERVERS_MAP.toString());
    }

    /**
     * 读取远程 yml 文件，使用 SnakeYml 库加载成初始的 HashMap
     * @return rawHashMap
     */
    private Map<String, Object> readConfigAsMap() {
        Request request = new Request.Builder()
                .url(this.serverConfigUrl)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("请求失败");
            }
            Yaml yaml = new Yaml();
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("响应体为空");
            }
            Map<String, Object> rawMap = yaml.load(body.byteStream());
            log.info("成功读取到远程的配置文件：" + rawMap.toString());
            return rawMap;
        } catch (IOException e) {
            throw new RuntimeException("请求远程 yml 配置失败", e);
        }
    }
}
