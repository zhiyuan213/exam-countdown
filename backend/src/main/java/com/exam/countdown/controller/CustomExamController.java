package com.exam.countdown.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exam.countdown.dto.CustomExamReq;
import com.exam.countdown.dto.QuotaResp;
import com.exam.countdown.entity.Subscription;
import com.exam.countdown.entity.UserExam;
import com.exam.countdown.mapper.SubscriptionMapper;
import com.exam.countdown.mapper.UserExamMapper;
import com.exam.countdown.service.QuotaService;
import com.exam.countdown.support.OpenidSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户自建提醒（「自定义提醒」）。
 *
 *   GET    /api/exams/custom        自定义提醒列表 + 剩余额度
 *   POST   /api/exams/custom        新建提醒（受额度限制）
 *   DELETE /api/exams/custom/{id}   删除提醒（逻辑删除，释放额度）
 *
 * 注意：{id} 用的是「对前端暴露的 id」＝ UserExam.ID_BASE + 本表 id，
 * 与 /api/exams 返回的自建节点 id 一致，前端可以直接透传。
 */
@RestController
@RequestMapping("/api/exams/custom")
public class CustomExamController {

    /** 订阅消息 thing 字段限制 20 字 */
    private static final int NAME_MAX = 20;

    private final UserExamMapper userExamMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final QuotaService quotaService;

    /** 全局默认模板 ID：自建提醒建库时就写入，前端拿它去申请订阅授权 */
    @Value("${wx.template-id:}")
    private String templateId;

    @Value("${wx.appid:}")
    private String appid;

    public CustomExamController(UserExamMapper userExamMapper,
                                SubscriptionMapper subscriptionMapper,
                                QuotaService quotaService) {
        this.userExamMapper = userExamMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.quotaService = quotaService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestHeader(value = "X-WX-FROM-OPENID", required = false) String fromOpenid,
                                    @RequestHeader(value = "X-WX-OPENID", required = false) String cloudOpenid) {
        String openid = OpenidSupport.resolve(fromOpenid, cloudOpenid, null);
        Map<String, Object> out = new LinkedHashMap<>();
        if (openid == null) {
            out.put("ok", false);
            out.put("msg", "openid 缺失，请重新进入小程序");
            return out;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (UserExam ue : selectMine(openid)) {
            items.add(item(ue));
        }
        out.put("ok", true);
        out.put("quota", quotaService.view(openid));
        out.put("items", items);
        return out;
    }

    @PostMapping
    public Map<String, Object> create(@RequestHeader(value = "X-WX-FROM-OPENID", required = false) String fromOpenid,
                                      @RequestHeader(value = "X-WX-OPENID", required = false) String cloudOpenid,
                                      @RequestBody CustomExamReq req) {
        String openid = OpenidSupport.resolve(fromOpenid, cloudOpenid, req == null ? null : req.getOpenid());
        if (openid == null) {
            return fail("openid 缺失，请重新进入小程序");
        }
        if (req == null || req.getName() == null || req.getName().trim().isEmpty()) {
            return fail("请填写提醒名称");
        }
        String name = req.getName().trim();
        if (name.length() > NAME_MAX) {
            return fail("名称不能超过 " + NAME_MAX + " 个字");
        }
        if (req.getExamDate() == null) {
            return fail("请选择日期");
        }
        if (req.getExamDate().isBefore(LocalDate.now())) {
            return fail("日期不能早于今天");
        }

        QuotaResp quota = quotaService.view(openid);
        if (quota.getRemaining() <= 0) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", false);
            out.put("code", "QUOTA_EXCEEDED");
            out.put("msg", "提醒额度已用完（" + quota.getUsed() + "/" + quota.getLimit() + "），删除旧提醒或扩容后再添加");
            out.put("quota", quota);
            return out;
        }

        UserExam ue = new UserExam();
        ue.setOpenid(openid);
        ue.setName(name);
        ue.setExamDate(req.getExamDate());
        ue.setTemplateId(templateId == null || templateId.isBlank() ? null : templateId.trim());
        ue.setAppid(appid == null || appid.isBlank() ? null : appid.trim());
        ue.setStatus(1);
        userExamMapper.insert(ue);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("item", item(ue));
        out.put("quota", quotaService.view(openid));
        return out;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@RequestHeader(value = "X-WX-FROM-OPENID", required = false) String fromOpenid,
                                      @RequestHeader(value = "X-WX-OPENID", required = false) String cloudOpenid,
                                      @PathVariable("id") Long id) {
        String openid = OpenidSupport.resolve(fromOpenid, cloudOpenid, null);
        if (openid == null) {
            return fail("openid 缺失，请重新进入小程序");
        }
        Long localId = UserExam.toLocalId(id);
        if (localId == null) {
            return fail("参数错误：不是自建提醒 id");
        }
        UserExam ue = userExamMapper.selectById(localId);
        if (ue == null || !openid.equals(ue.getOpenid())) {
            return fail("提醒不存在或不属于你");
        }

        ue.setStatus(0);
        userExamMapper.updateById(ue);
        // 一并清掉该节点的订阅额度，避免残留记录影响推送
        subscriptionMapper.delete(new QueryWrapper<Subscription>().eq("exam_id", id));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("quota", quotaService.view(openid));
        return out;
    }

    private List<UserExam> selectMine(String openid) {
        return userExamMapper.selectList(new QueryWrapper<UserExam>()
                .eq("openid", openid)
                .eq("status", 1)
                .orderByAsc("exam_date"));
    }

    /** 与 /api/exams 官方节点保持同一套字段，额外带 isCustom 标记 */
    private Map<String, Object> item(UserExam ue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", UserExam.toPublicId(ue.getId()));
        m.put("name", ue.getName());
        m.put("examDate", ue.getExamDate() == null ? null : ue.getExamDate().toString());
        m.put("templateId", ue.getTemplateId());
        m.put("nodeType", "CUSTOM");
        m.put("groupCode", "custom");
        m.put("groupName", "自定义提醒");
        m.put("sort", 0);
        m.put("isCustom", true);
        m.put("daysLeft", ue.getExamDate() == null ? null
                : ChronoUnit.DAYS.between(LocalDate.now(), ue.getExamDate()));
        return m;
    }

    private Map<String, Object> fail(String msg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", false);
        out.put("msg", msg);
        return out;
    }
}
