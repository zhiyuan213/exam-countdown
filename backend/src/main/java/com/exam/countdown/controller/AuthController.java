package com.exam.countdown.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exam.countdown.dto.LoginReq;
import com.exam.countdown.dto.LoginResp;
import com.exam.countdown.entity.WxUser;
import com.exam.countdown.mapper.WxUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 登录。两条取 openid 的路径：
 *  1) 云托管 callContainer：平台注入 X-WX-OPENID / X-WX-FROM-OPENID 请求头（线上主路径）
 *  2) 兜底：前端传 code，后端走 jscode2session
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper om = new ObjectMapper();
    private final WxUserMapper wxUserMapper;

    @Value("${wx.appid}")
    private String appid;

    @Value("${wx.secret}")
    private String secret;

    public AuthController(WxUserMapper wxUserMapper) {
        this.wxUserMapper = wxUserMapper;
    }

    @PostMapping("/login")
    public LoginResp login(@RequestHeader(value = "X-WX-FROM-OPENID", required = false) String fromOpenid,
                           @RequestHeader(value = "X-WX-OPENID", required = false) String cloudOpenid,
                           @RequestHeader(value = "X-WX-FROM-APPID", required = false) String fromAppid,
                           @RequestHeader(value = "X-WX-APPID", required = false) String wxAppid,
                           @RequestBody(required = false) LoginReq req) throws Exception {
        log.info("login headers: X-WX-OPENID={}, X-WX-FROM-OPENID={}, X-WX-APPID={}, X-WX-FROM-APPID={}",
                cloudOpenid, fromOpenid, wxAppid, fromAppid);

        String openid = firstNotBlank(fromOpenid, cloudOpenid);
        if (openid == null && req != null && req.getCode() != null && !req.getCode().isBlank()) {
            openid = code2session(req.getCode());
        }
        if (openid == null) {
            throw new RuntimeException("缺少身份请求头（请确认小程序走 wx.cloud.callContainer / wx.cloud.Cloud 调用）");
        }

        if (wxUserMapper.selectCount(new QueryWrapper<WxUser>().eq("openid", openid)) == 0L) {
            WxUser u = new WxUser();
            u.setOpenid(openid);
            wxUserMapper.insert(u);
        }
        return new LoginResp(openid);
    }

    private static String firstNotBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return (b != null && !b.isBlank()) ? b.trim() : null;
    }

    private String code2session(String code) throws Exception {
        String url = String.format(
                "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appid, secret, code);
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        JsonNode node = om.readTree(resp.body());
        if (!node.has("openid")) {
            log.error("code2session 失败: {}", resp.body());
            throw new RuntimeException("code2session 失败: " + resp.body());
        }
        return node.get("openid").asText();
    }
}
