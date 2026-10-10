package com.exam.countdown.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exam.countdown.dto.SubscribeReq;
import com.exam.countdown.entity.Exam;
import com.exam.countdown.entity.Subscription;
import com.exam.countdown.entity.UserExam;
import com.exam.countdown.mapper.ExamMapper;
import com.exam.countdown.mapper.SubscriptionMapper;
import com.exam.countdown.mapper.UserExamMapper;
import com.exam.countdown.support.OpenidSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 订阅提醒。一次调用 = 一次推送额度（remain_times +1）。
 *
 * 两类节点共用本接口：
 *   - 官方节点：examId 直接是 exam.id
 *   - 用户自建节点：examId = UserExam.ID_BASE + user_exam.id
 * subscription.exam_id 存的就是这个「对前端暴露的 id」，推送时按同一规则回查。
 */
@RestController
@RequestMapping("/api/subscribe")
public class SubscribeController {

    private final SubscriptionMapper subscriptionMapper;
    private final ExamMapper examMapper;
    private final UserExamMapper userExamMapper;

    @Value("${wx.template-id:}")
    private String templateId;

    public SubscribeController(SubscriptionMapper subscriptionMapper,
                               ExamMapper examMapper,
                               UserExamMapper userExamMapper) {
        this.subscriptionMapper = subscriptionMapper;
        this.examMapper = examMapper;
        this.userExamMapper = userExamMapper;
    }

    @PostMapping
    public Map<String, Object> subscribe(
            @RequestHeader(value = "X-WX-FROM-OPENID", required = false) String fromOpenid,
            @RequestHeader(value = "X-WX-OPENID", required = false) String cloudOpenid,
            @RequestBody SubscribeReq req) {

        String openid = OpenidSupport.resolve(fromOpenid, cloudOpenid, req == null ? null : req.getOpenid());
        if (openid == null) {
            return Map.of("ok", false, "msg", "openid 缺失，请重新进入小程序");
        }
        if (req == null || req.getExamId() == null) {
            return Map.of("ok", false, "msg", "缺少节点 id");
        }

        String tpl;
        if (UserExam.isCustomId(req.getExamId())) {
            // 自建节点：必须是自己创建的，才允许登记订阅额度
            Long localId = UserExam.toLocalId(req.getExamId());
            UserExam ue = localId == null ? null : userExamMapper.selectById(localId);
            if (ue == null || ue.getStatus() == null || ue.getStatus() != 1) {
                return Map.of("ok", false, "msg", "提醒不存在或已删除");
            }
            if (!openid.equals(ue.getOpenid())) {
                return Map.of("ok", false, "msg", "无权订阅他人的提醒");
            }
            tpl = (ue.getTemplateId() != null && !ue.getTemplateId().isBlank())
                    ? ue.getTemplateId() : templateId;
        } else {
            Exam exam = examMapper.selectById(req.getExamId());
            if (exam == null) {
                return Map.of("ok", false, "msg", "考试不存在");
            }
            tpl = (exam.getTemplateId() != null && !exam.getTemplateId().isBlank())
                    ? exam.getTemplateId() : templateId;
        }

        Subscription sub = subscriptionMapper.selectOne(
                new QueryWrapper<Subscription>()
                        .eq("openid", openid)
                        .eq("exam_id", req.getExamId()));

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

    /**
     * 当前用户已订阅（remain_times > 0）的节点列表，
     * 前端用来把「订阅提醒」按钮渲染成「已订阅」状态。
     * 返回 items: [{examId, remainTimes}]
     */
    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestHeader(value = "X-WX-FROM-OPENID", required = false) String fromOpenid,
            @RequestHeader(value = "X-WX-OPENID", required = false) String cloudOpenid) {

        String openid = OpenidSupport.resolve(fromOpenid, cloudOpenid, null);
        if (openid == null) {
            return Map.of("ok", false, "msg", "openid 缺失，请重新进入小程序");
        }

        List<Subscription> subs = subscriptionMapper.selectList(
                new QueryWrapper<Subscription>()
                        .eq("openid", openid)
                        .gt("remain_times", 0)
                        .select("exam_id", "remain_times"));

        List<Map<String, Object>> items = new ArrayList<>();
        for (Subscription s : subs) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("examId", s.getExamId());
            item.put("remainTimes", s.getRemainTimes());
            items.add(item);
        }
        return Map.of("ok", true, "items", items);
    }
}
