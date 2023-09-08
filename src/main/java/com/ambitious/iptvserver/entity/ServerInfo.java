package com.ambitious.iptvserver.entity;

import cn.hutool.core.util.StrUtil;
import lombok.Data;

/**
 * 存放直播源的相关信息
 * @author ambitious
 * @date 2023/9/6
 */
@Data
public class ServerInfo {

    /**
     * 直播源地址
     */
    private final String url;
    /**
     * 总请求数
     */
    private Double requestTotalNum;
    /**
     * 请求成功数
     */
    private Double requestSuccessNum;
    /**
     * 请求成功的比率
     */
    private Double successRate;

    public ServerInfo(String url) {
        this.url = url;
        this.requestTotalNum = 0.0;
        this.requestSuccessNum = 0.0;
        this.successRate = 0.0;
    }

    /**
     * 添加请求记录
     * @param success 是否请求成功
     */
    public void addRecord(boolean success) {
        if (success) {
            requestSuccessNum++;
        }
        requestTotalNum++;
        successRate = requestSuccessNum / requestTotalNum;
    }

    @Override
    public int hashCode() {
        return this.url.hashCode();
    }

    @Override
    public boolean equals(Object another) {
        if (!(another instanceof ServerInfo) || StrUtil.isEmpty(this.url)) {
            return false;
        }
        return this.url.equals(((ServerInfo) another).getUrl());
    }
}
