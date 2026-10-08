package com.exam.countdown.dto;

import lombok.Data;

/**
 * 订阅请求。用户点击「订阅提醒」时调用，一次授权 = 一次推送额度。
 * openid 优先取云托管请求头，兜底用 body 里的字段。
 */
@Data
public class SubscribeReq {
    private String openid;
    private Long examId;
}
