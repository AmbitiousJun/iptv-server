package com.ambitious.iptvserver.controller;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author ambitious
 * @date 2023/10/8
 */
@RestControllerAdvice
public class ExceptionAdvice {

    @ExceptionHandler(Exception.class)
    public String handleException(Exception e) {
        return "请求异常：" + e.getMessage();
    }
}
