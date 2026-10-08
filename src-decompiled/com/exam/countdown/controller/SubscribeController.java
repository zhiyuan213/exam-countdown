package com.exam.countdown.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exam.countdown.dto.SubscribeReq;
import com.exam.countdown.entity.Exam;
import com.exam.countdown.entity.Subscription;
import com.exam.countdown.mapper.ExamMapper;
import com.exam.countdown.mapper.SubscriptionMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/subscribe"})
public class SubscribeController {
   private final SubscriptionMapper subscriptionMapper;
   private final ExamMapper examMapper;
   @Value("${wx.template-id}")
   private String templateId;

   public SubscribeController(SubscriptionMapper subscriptionMapper, ExamMapper examMapper) {
      this.subscriptionMapper = subscriptionMapper;
      this.examMapper = examMapper;
   }

   @PostMapping
   public Map<String, Object> subscribe(
      @RequestHeader(value = "X-WX-FROM-OPENID",required = false) String fromOpenid,
      @RequestHeader(value = "X-WX-OPENID",required = false) String cloudOpenid,
      @RequestBody SubscribeReq req
   ) {
      String openid = firstNotBlank(fromOpenid, cloudOpenid);
      if (openid == null) {
         openid = req.getOpenid();
      }

      if (openid != null && !openid.isBlank()) {
         Exam exam = (Exam)this.examMapper.selectById(req.getExamId());
         if (exam == null) {
            return Map.of("ok", false, "msg", "考试不存在");
         } else {
            Subscription sub = (Subscription)this.subscriptionMapper
               .selectOne((Wrapper)((QueryWrapper)new QueryWrapper().eq("openid", openid)).eq("exam_id", req.getExamId()));
            String tpl = exam.getTemplateId() != null && !exam.getTemplateId().isBlank() ? exam.getTemplateId() : this.templateId;
            if (sub == null) {
               sub = new Subscription();
               sub.setOpenid(openid);
               sub.setExamId(req.getExamId());
               sub.setTemplateId(tpl);
               sub.setRemainTimes(1);
               this.subscriptionMapper.insert(sub);
            } else {
               sub.setTemplateId(tpl);
               sub.setRemainTimes(sub.getRemainTimes() + 1);
               this.subscriptionMapper.updateById(sub);
            }

            return Map.of("ok", true, "remainTimes", sub.getRemainTimes());
         }
      } else {
         return Map.of("ok", false, "msg", "openid 缺失，请重新进入小程序");
      }
   }

   private static String firstNotBlank(String a, String b) {
      if (a != null && !a.isBlank()) {
         return a.trim();
      } else {
         return b != null && !b.isBlank() ? b.trim() : null;
      }
   }
}
