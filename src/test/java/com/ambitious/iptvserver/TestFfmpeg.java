package com.ambitious.iptvserver;

import cn.hutool.core.util.StrUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 测试本地路径下的 Ffmpeg 是否可用
 * @author ambitious
 * @date 2023/9/6
 */
@SpringBootTest
public class TestFfmpeg {

    @Test
    void test() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("./ffmpeg-mac", "-i", "https://php.17186.eu.org/gdtv/web/xwpd.m3u8");
        // ProcessBuilder pb = new ProcessBuilder("ls", "-la");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader bf = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line = bf.readLine();
        while (StrUtil.isNotEmpty(line)) {
            System.out.println(line);
            line = bf.readLine();
        }
        p.waitFor();
    }

    @Test
    void testRegex() {
        String regex = "Input(.*)from '(.+)':";
        Pattern pattern = Pattern.compile(regex);
        Matcher m = pattern.matcher("Input #0, hls, from 'https://php.17186.eu.org/gdtv/web/xwpd.m3u8':");
        if (m.matches()) {
            int cnt = m.groupCount();
            for (int i = 0; i <= cnt; i++) {
                System.out.println(m.group(i));
            }
        }
    }
}
