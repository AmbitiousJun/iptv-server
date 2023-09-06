package com.ambitious.iptvserver.job.service;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 使用 ffmpeg 来分析直播源是否可用
 * @author ambitious
 * @date 2023/9/6
 */
@Service
@Slf4j
public class FfmpegServerTest implements ServerTest {

    @Value("${os}")
    private String os;

    @Override
    public boolean test(String url) {
        // 1 准备好正则表达式
        String regex = "Input(.*)from '(.+)':";
        Pattern pattern = Pattern.compile(regex);
        // 2 调用 ffmpeg-mac，分析直播源的可用性
        ProcessBuilder pb = new ProcessBuilder("./ffmpeg/ffmpeg-" + os, "-i", url);
        pb.redirectErrorStream(true);
        log.info("ffmpeg url: {}", url);
        try {
            Process p = pb.start();
            BufferedReader bf = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = bf.readLine();
            while (StrUtil.isNotEmpty(line)) {
                Matcher m = pattern.matcher(line);
                if (m.matches() && url.equals(m.group(2))) {
                    return true;
                }
                log.info(line);
                line = bf.readLine();
            }
            p.waitFor();
            log.info("ffmpeg url: {}", url);
        } catch (Exception e) {
            log.error("调用 ffmpeg 异常：{}", e.getMessage());
            return false;
        }
        return false;
    }
}
