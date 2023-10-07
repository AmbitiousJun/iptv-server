package com.ambitious.iptvserver.config;

import com.ambitious.iptvserver.entity.ServerInfo;
import com.ambitious.iptvserver.entity.ServerProxy;
import com.ambitious.iptvserver.util.CastUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    /**
     * 直播源代理配置
     */
    private List<ServerProxy> proxies;
    /**
     * 静态的直播源代理配置
     */
    private static List<ServerProxy> staticProxies;
    /**
     * 需要代理的直播源 host
     */
    private static Set<String> proxyHosts;
    /**
     * 用于匹配出 url 中的主机名的正则表达式
     */
    private static final Pattern PROXY_HOST_PATTERN = Pattern.compile("https?://([^/]+)");
    @Resource
    private OkHttpClient httpClient;
    private static Map<String, List<ServerInfo>> SERVERS_MAP = Maps.newHashMap();
    /**
     * 用于刷新直播源数据时进行线程同步
     */
    public static final ReentrantReadWriteLock SERVERS_LOCK = new ReentrantReadWriteLock();

    /**
     * 获取一个读取直播源的读锁
     * @return 读锁
     */
    public static Lock getServersReadLock() {
        return SERVERS_LOCK.readLock();
    }

    public static Lock getServersWriteLock() {
        return SERVERS_LOCK.writeLock();
    }

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
        Lock lock = getServersReadLock();
        lock.lock();
        try {
            return Lists.newArrayList(SERVERS_MAP.keySet());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 根据电视台名称从 SERVERS_MAP 中获取对应电视台的直播源列表
     * @param tvName 电视台名称
     * @return 直播源列表，如果不存在该电视台的配置，就返回空
     */
    public static List<ServerInfo> getServers(String tvName) {
        Lock lock = getServersReadLock();
        lock.lock();
        try {
            return SERVERS_MAP.get(tvName);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 读取远程的 yml 配置文件，将数据源预处理成一个 HashMap
     */
    @Override
    public void afterPropertiesSet() {
        Map<String, Object> rawMap = readConfigAsMap();
        formatServersMap(rawMap);
        initProxyHosts();
        staticProxies = this.proxies;
    }

    /**
     * 获取一个直播源的代理请求头
     * @param url url
     */
    public static Map<String, String> getProxyHeaders(String url) {
        Map<String, String> res = Maps.newHashMap();
        for (ServerProxy serverProxy : staticProxies) {
            String host = serverProxy.getHost();
            Matcher matcher = PROXY_HOST_PATTERN.matcher(url);
            if (!matcher.find() || !matcher.group(1).equals(host)) {
                continue;
            }
            List<String> headers = serverProxy.getHeaders();
            for (String header : headers) {
                String[] kvs = header.split("\\|");
                if (kvs.length != 2) {
                    throw new RuntimeException("代理请求头配置错误：" + header + "，请使用 | 分割 key value");
                }
                res.put(kvs[0], kvs[1]);
            }
        }
        return res;
    }

    /**
     * 检查某个直播源是否需要本地代理
     * @param url url
     * @return 是否需要代理
     */
    public static boolean checkNeedProxy(String url) {
        // 解析主机名
        Matcher matcher = PROXY_HOST_PATTERN.matcher(url);
        if (matcher.find()) {
            return proxyHosts.contains(matcher.group(1));
        }
        return proxyHosts.contains(url);
    }

    /**
     * 初始化需要进行代理的直播源主机集合
     */
    private void initProxyHosts() {
        proxyHosts = Sets.newHashSet();
        for (ServerProxy serverProxy : proxies) {
            proxyHosts.add(serverProxy.getHost());
        }
    }

    /**
     * 刷新直播源数据
     * @return 是否刷新成功
     */
    public boolean refreshServers() {
        if (SERVERS_LOCK.getReadLockCount() > 0) {
            return false;
        }
        Lock lock = getServersWriteLock();
        lock.lock();
        try {
            Map<String, List<ServerInfo>> oldMap = SERVERS_MAP;
            SERVERS_MAP = Maps.newHashMap();
            formatServersMap(readConfigAsMap());
            // 对比 newMap，如果 oldMap 中存在一样的直播源，则拷贝得分数据
            for (String tvKey : SERVERS_MAP.keySet()) {
                if (!oldMap.containsKey(tvKey)) {
                    continue;
                }
                Set<ServerInfo> olds = Sets.newHashSet(oldMap.get(tvKey));
                Map<String, List<ServerInfo>> oldUrlMap = oldMap.get(tvKey).stream().collect(Collectors.groupingBy(ServerInfo::getUrl));
                List<ServerInfo> newServers = SERVERS_MAP.get(tvKey);
                for (ServerInfo newServer : newServers) {
                    if (olds.contains(newServer)) {
                        String url = newServer.getUrl();
                        ServerInfo oldInfo = oldUrlMap.get(url).get(0);
                        newServer.setRequestSuccessNum(oldInfo.getRequestSuccessNum());
                        newServer.setSuccessRate(oldInfo.getSuccessRate());
                        newServer.setRequestTotalNum(oldInfo.getRequestTotalNum());
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.error("更新直播源数据失败：{}", e.getMessage());
            return false;
        } finally {
            lock.unlock();
        }
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
