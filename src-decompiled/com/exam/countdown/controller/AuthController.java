package com.exam.countdown.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exam.countdown.dto.LoginReq;
import com.exam.countdown.dto.LoginResp;
import com.exam.countdown.entity.WxUser;
import com.exam.countdown.mapper.WxUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth"})
public class AuthController {
   private static final Logger log = LoggerFactory.getLogger(AuthController.class);
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
   private final ObjectMapper om = new ObjectMapper();
   private final WxUserMapper wxUserMapper;
   @Value("${wx.appid}")
   private String appid;
   @Value("${wx.secret}")
   private String secret;

   public AuthController(WxUserMapper wxUserMapper) {
      this.wxUserMapper = wxUserMapper;
   }

   @PostMapping({"/login"})
   public LoginResp login(
      @RequestHeader(value = "X-WX-FROM-OPENID",required = false) String fromOpenid,
      @RequestHeader(value = "X-WX-OPENID",required = false) String cloudOpenid,
      @RequestHeader(value = "X-WX-FROM-APPID",required = false) String fromAppid,
      @RequestHeader(value = "X-WX-APPID",required = false) String wxAppid,
      @RequestBody(required = false) LoginReq req
   ) throws Exception {
      log.info(
         "login headers: X-WX-OPENID={}, X-WX-FROM-OPENID={}, X-WX-APPID={}, X-WX-FROM-APPID={}", new Object[]{cloudOpenid, fromOpenid, wxAppid, fromAppid}
      );
      String openid = firstNotBlank(fromOpenid, cloudOpenid);
      if (openid == null && req != null && req.getCode() != null && !req.getCode().isBlank()) {
         openid = this.code2session(req.getCode());
      }

      if (openid == null) {
         throw new RuntimeException("缺少身份请求头（请确认小程序走 wx.cloud.callContainer / wx.cloud.Cloud 调用）");
      } else {
         if (this.wxUserMapper.selectCount((Wrapper)new QueryWrapper().eq("openid", openid)) == 0L) {
            WxUser u = new WxUser();
            u.setOpenid(openid);
            this.wxUserMapper.insert(u);
         }

         return new LoginResp(openid);
      }
   }

   private static String firstNotBlank(String a, String b) {
      if (a != null && !a.isBlank()) {
         return a.trim();
      } else {
         return b != null && !b.isBlank() ? b.trim() : null;
      }
   }

   private String code2session(String code) throws Exception {
      String url = String.format(
         "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code", this.appid, this.secret, code
      );
      HttpResponse<String> resp = this.http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), BodyHandlers.ofString());
      JsonNode node = this.om.readTree(resp.body());
      if (!node.has("openid")) {
         log.error("code2session 失败: {}", resp.body());
         throw new RuntimeException("code2session 失败: " + resp.body());
      } else {
         return node.get("openid").asText();
      }
   }
}
