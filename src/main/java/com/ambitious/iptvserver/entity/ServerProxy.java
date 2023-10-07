package com.ambitious.iptvserver.entity;

import lombok.Data;

import java.util.List;

/**
 * 直播源代理信息
 * @author ambitious
 * @date 2023/10/7
 */
@Data
public class ServerProxy {

    private String host;
    private List<String> headers;
}
