package com.ambitious.iptvserver.job.service;

/**
 * 测试直播源地址的连通性
 * @author ambitious
 * @date 2023/9/6
 */
public interface ServerTest {

    /**
     * 测试连通性
     * @param url 要测试的直播源地址
     * @return 测试是否成功
     */
    boolean test(String url);
}
