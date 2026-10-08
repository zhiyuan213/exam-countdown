package com.exam.countdown.dto;

import lombok.Data;

/**
 * 登录请求。走云托管 callContainer 时 openid 由平台请求头注入，
 * 只有本地 / 非云托管部署才需要前端传 code 走 jscode2session。
 */
@Data
public class LoginReq {
    private String code;
}
