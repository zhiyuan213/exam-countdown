package com.exam.countdown.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenManager {
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   private final ObjectMapper om = new ObjectMapper();
   @Value("${wx.appid}")
   private String appid;
   @Value("${wx.secret}")
   private String secret;
   private volatile String token;
   private volatile long expireAt = 0L;

   public synchronized String get() {
      if (System.currentTimeMillis() < this.expireAt - 60000L) {
         return this.token;
      } else {
         try {
            String url = String.format("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s", this.appid, this.secret);
            HttpResponse<String> resp = this.http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), BodyHandlers.ofString());
            JsonNode node = this.om.readTree(resp.body());
            if (node.has("access_token")) {
               this.token = node.get("access_token").asText();
               this.expireAt = System.currentTimeMillis() + node.get("expires_in").asLong() * 1000L;
               return this.token;
            } else {
               throw new RuntimeException("获取 access_token 失败: " + resp.body());
            }
         } catch (Exception var4) {
            throw new RuntimeException("获取 access_token 异常", var4);
         }
      }
   }

   public synchronized String refresh() {
      this.expireAt = 0L;
      return this.get();
   }
}
