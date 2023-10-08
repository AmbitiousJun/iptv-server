package com.ambitious.iptvserver.job;

import com.ambitious.iptvserver.config.IptvConfig;
import com.ambitious.iptvserver.entity.ServerInfo;
import com.ambitious.iptvserver.job.service.ServerTest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * 每隔 30 分钟就对所有直播源进行一次请求，
 * 更新直播源评分后，
 * 依据评分重新进行排序
 * @author ambitious
 * @date 2023/9/6
 */
// @Component
@Slf4j
public class ServerScoringJob {

    /**
     * 时间格式化
     */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /**
     * 时区
     */
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * 直播源连通性测试
     */
    @Resource(name = "ffmpegServerTest")
    private ServerTest serverTest;

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    public void doJob() {
        Lock lock = IptvConfig.getServersReadLock();
        lock.lock();
        try {
            log.info("开始更新直播源的评分...");
            List<String> tvKeys = IptvConfig.getAllTypes();
            for (String tvKey : tvKeys) {
                // 拿到电视台的所有直播源
                List<ServerInfo> servers = IptvConfig.getServers(tvKey);
                for (ServerInfo serverInfo : servers) {
                    // 如果是需要进行代理的直播源，就不进行测试
                    if (IptvConfig.checkNeedProxy(serverInfo.getUrl())) {
                        serverInfo.addRecord(true);
                    } else {
                        // 测试是否能够成功连接
                        serverInfo.addRecord(serverTest.test(serverInfo.getUrl()));
                    }
                }
                IptvConfig.reSort(tvKey);
                printTvScore(tvKey);
            }
            log.info("直播源评分更新完成，下次更新时间：" + FORMATTER.format(ZonedDateTime.now(ZONE_ID).toLocalDateTime().plusMinutes(30)));
        } finally {
            lock.unlock();
        }
    }

    /**
     * 打印直播源得分
     * @param tvKey tvKey
     */
    public void printTvScore(String tvKey) {
        System.out.println("====== tvKey: " + tvKey + " ↓ ======");
        List<ServerInfo> servers = IptvConfig.getServers(tvKey);
        for (ServerInfo serverInfo : servers) {
            int score = (int) Math.ceil(serverInfo.getSuccessRate() * 100);
            System.out.printf("== [score]: %d [url]: %s ==\n", score, serverInfo.getUrl());
        }
        System.out.println("====== tvKey: " + tvKey + " ↑ ======");
    }
}
