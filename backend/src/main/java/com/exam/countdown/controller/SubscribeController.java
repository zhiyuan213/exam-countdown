package com.exam.countdown.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exam.countdown.dto.SubscribeReq;
import com.exam.countdown.entity.Exam;
import com.exam.countdown.entity.Subscription;
import com.exam.countdown.mapper.ExamMapper;
import com.exam.countdown.mapper.SubscriptionMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订阅提醒。一次调用 = 一次推送额度（remain_times +1）。
 */
@RestController
@RequestMapping("/api/subscribe")
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
            @RequestHeader(value = "X-WX-FROM-OPENID", required = false) String fromOpenid,
            @RequestHeader(value = "X-WX-OPENID", required = false) String cloudOpenid,
            @RequestBody SubscribeReq req) {

        String openid = firstNotBlank(fromOpenid, cloudOpenid);
        if (openid == null) {
            openid = req.getOpenid();
        }
        if (openid == null || openid.isBlank()) {
            return Map.of("ok", false, "msg", "openid 缺失，请重新进入小程序");
        }

        Exam exam = examMapper.selectById(req.getExamId());
        if (exam == null) {
            return Map.of("ok", false, "msg", "考试不存在");
        }

        Subscription sub = subscriptionMapper.selectOne(
                new QueryWrapper<Subscription>()
                        .eq("openid", openid)
                        .eq("exam_id", req.getExamId()));

        // 模板 ID 以后端配置为准
        String tpl = (exam.getTemplateId() != null && !exam.getTemplateId().isBlank())
                ? exam.getTemplateId() : templateId;

        if (sub == null) {
            sub = new Subscription();
            sub.setOpenid(openid);
            sub.setExamId(req.getExamId());
            sub.setTemplateId(tpl);
            sub.setRemainTimes(1);
            subscriptionMapper.insert(sub);
        } else {
            sub.setTemplateId(tpl);
            sub.setRemainTimes(sub.getRemainTimes() + 1);
            subscriptionMapper.updateById(sub);
        }
        return Map.of("ok", true, "remainTimes", sub.getRemainTimes());
    }

    private static String firstNotBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return (b != null && !b.isBlank()) ? b.trim() : null;
    }
}
