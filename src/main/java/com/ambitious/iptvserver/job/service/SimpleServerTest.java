package com.ambitious.iptvserver.job.service;

import cn.hutool.http.HttpStatus;
import com.ambitious.iptvserver.entity.ServerInfo;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 通过发送简单的 http 请求来判断直播源的连通性
 * @author ambitious
 * @date 2023/9/6
 */
@Service
public class SimpleServerTest implements ServerTest {

    @Resource
    private OkHttpClient httpClient;

    @Override
    public boolean test(String url) {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.code() == HttpStatus.HTTP_OK) {
                return true;
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }
}
