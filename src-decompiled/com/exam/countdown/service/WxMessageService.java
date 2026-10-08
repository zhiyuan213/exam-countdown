package com.exam.countdown.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WxMessageService {
   private static final Logger log = LoggerFactory.getLogger(WxMessageService.class);
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   private final ObjectMapper om = new ObjectMapper();
   private final TokenManager tokenManager;
   @Value("${wx.cloud-call:true}")
   private boolean cloudCallFirst;
   @Value("${wx.appid:}")
   private String envAppid;

   public WxMessageService(TokenManager tokenManager) {
      this.tokenManager = tokenManager;
   }

   public int send(String openid, String templateId, String page, Map<String, Object> data, String fromAppid) {
      if (this.cloudCallFirst) {
         try {
            return this.cloudSend(openid, templateId, page, data, fromAppid);
         } catch (Exception var8) {
            log.warn("云调用通道异常，降级到公网 token 通道: {}", var8.toString());
         }
      }

      try {
         int errcode = this.doSend(openid, templateId, page, data, this.tokenManager.get());
         if (errcode == 40001 || errcode == 42001) {
            errcode = this.doSend(openid, templateId, page, data, this.tokenManager.refresh());
         }

         return errcode;
      } catch (Exception var7) {
         log.error("订阅消息发送失败: {}", var7.toString());
         return -1;
      }
   }

   private int cloudSend(String openid, String templateId, String page, Map<String, Object> data, String fromAppid) throws Exception {
      String url = "http://api.weixin.qq.com/cgi-bin/message/subscribe/send";
      if (fromAppid != null && !fromAppid.isBlank() && !fromAppid.equals(this.envAppid)) {
         log.info("云调用使用资源复用身份 from_appid={}", fromAppid);
         url = url + "?from_appid=" + fromAppid;
      }

      Map<String, Object> body = Map.of("touser", openid, "template_id", templateId, "page", page, "data", data);
      HttpRequest req = HttpRequest.newBuilder(URI.create(url))
         .header("Content-Type", "application/json")
         .POST(BodyPublishers.ofString(this.om.writeValueAsString(body)))
         .build();
      HttpResponse<String> resp = this.http.send(req, BodyHandlers.ofString());
      JsonNode node = this.om.readTree(resp.body());
      int errcode = node.has("errcode") ? node.get("errcode").asInt() : -1;
      if (errcode != 0) {
         log.warn("云调用返回 errcode={}: {}", errcode, resp.body());
      }

      return errcode;
   }

   private int doSend(String openid, String templateId, String page, Map<String, Object> data, String accessToken) throws Exception {
      String url = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=" + accessToken;
      Map<String, Object> body = Map.of("touser", openid, "template_id", templateId, "page", page, "data", data);
      HttpRequest req = HttpRequest.newBuilder(URI.create(url))
         .header("Content-Type", "application/json")
         .POST(BodyPublishers.ofString(this.om.writeValueAsString(body)))
         .build();
      HttpResponse<String> resp = this.http.send(req, BodyHandlers.ofString());
      JsonNode node = this.om.readTree(resp.body());
      return node.has("errcode") ? node.get("errcode").asInt() : -1;
   }
}
