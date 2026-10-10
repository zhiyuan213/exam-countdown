package com.exam.countdown.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 创建自定义提醒请求。
 * openid 优先取云托管请求头，兜底用本字段（本地调试）。
 */
@Data
public class CustomExamReq {

    /** 提醒名称，≤20 字 */
    private String name;

    /** 目标日期，格式 yyyy-MM-dd */
    private LocalDate examDate;

    /** 兜底 openid */
    private String openid;
}
