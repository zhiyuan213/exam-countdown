package com.exam.countdown.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 微信订阅消息发送。
 *
 * 两条通道：
 *  1) 云调用（默认）：请求 http://api.weixin.qq.com/... 由云托管平台注入 token 并转发，
 *     无需自己换 access_token。若需以「其他小程序身份」发送，在 URL 上追加 from_appid。
 *  2) 公网 + access_token：本地开发或云调用异常时降级使用。
 */
@Service
public class WxMessageService {

    private static final Logger log = LoggerFactory.getLogger(WxMessageService.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper om = new ObjectMapper();
    private final TokenManager tokenManager;

    @Value("${wx.cloud-call:true}")
    private boolean cloudCallFirst;

    @Value("${wx.appid:}")
    private String envAppid;

    public WxMessageService(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    /**
     * @return 微信 errcode，0 = 成功，-1 = 本地异常
     */
    public int send(String openid, String templateId, String page,
                    Map<String, Object> data, String fromAppid) {
        if (cloudCallFirst) {
            try {
                return cloudSend(openid, templateId, page, data, fromAppid);
            } catch (Exception e) {
                log.warn("云调用通道异常，降级到公网 token 通道: {}", e.toString());
            }
        }
        try {
            int errcode = doSend(openid, templateId, page, data, tokenManager.get());
            if (errcode == 40001 || errcode == 42001) {
                errcode = doSend(openid, templateId, page, data, tokenManager.refresh());
            }
            return errcode;
        } catch (Exception e) {
            log.error("订阅消息发送失败: {}", e.toString());
            return -1;
        }
    }

    private int cloudSend(String openid, String templateId, String page,
                          Map<String, Object> data, String fromAppid) throws Exception {
        String url = "http://api.weixin.qq.com/cgi-bin/message/subscribe/send";
        if (fromAppid != null && !fromAppid.isBlank() && !fromAppid.equals(envAppid)) {
            log.info("云调用使用资源复用身份 from_appid={}", fromAppid);
            url = url + "?from_appid=" + fromAppid;
        }
        Map<String, Object> body = Map.of(
                "touser", openid,
                "template_id", templateId,
                "page", page,
                "data", data);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode node = om.readTree(resp.body());
        int errcode = node.has("errcode") ? node.get("errcode").asInt() : -1;
        if (errcode != 0) {
            log.warn("云调用返回 errcode={}: {}", errcode, resp.body());
        }
        return errcode;
    }

    private int doSend(String openid, String templateId, String page,
                       Map<String, Object> data, String accessToken) throws Exception {
        String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken;
        Map<String, Object> body = Map.of(
                "touser", openid,
                "template_id", templateId,
                "page", page,
                "data", data);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        JsonNode node = om.readTree(resp.body());
        return node.has("errcode") ? node.get("errcode").asInt() : -1;
    }
}
