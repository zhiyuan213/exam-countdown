package com.exam.countdown.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exam.countdown.entity.Exam;
import com.exam.countdown.entity.PushLog;
import com.exam.countdown.entity.Subscription;
import com.exam.countdown.entity.UserExam;
import com.exam.countdown.mapper.ExamMapper;
import com.exam.countdown.mapper.PushLogMapper;
import com.exam.countdown.mapper.SubscriptionMapper;
import com.exam.countdown.mapper.UserExamMapper;
import com.exam.countdown.service.WxMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 提醒任务。每天定时扫一遍上架节点，命中「考前 N 天」就按订阅额度推送。
 *
 * 触发方式二选一：
 *  - 应用内 @Scheduled（cron 由 remind.cron 控制，默认每天 08:00）
 *  - 云托管定时任务 GET /api/job/remind?token=***（同一份逻辑，便于手动/外部触发）
 */
@Component
public class RemindJob {

    private static final Logger log = LoggerFactory.getLogger(RemindJob.class);
    private static final DateTimeFormatter CN_DATETIME = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");

    private final ExamMapper examMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final PushLogMapper pushLogMapper;
    private final UserExamMapper userExamMapper;
    private final WxMessageService wxMessageService;

    /** 全局默认模板 ID；exam.template_id 为空时用它 */
    @Value("${wx.template-id}")
    private String templateId;

    /** 提醒节点：兜底配置（按类型的配置里没命中时用它） */
    @Value("${remind.nodes:30,7,1,0}")
    private String nodesConfig;

    /**
     * 按节点类型区分的提醒点，格式 "TYPE:节点,节点;TYPE:节点"。
     *
     * 为什么要按类型区分：微信「一次授权 = 一条推送」，而推送是命中最靠前的提醒点就扣额度。
     * 如果所有类型都用 30/7/1/0，用户订 1 次只会收到「考前 30 天」那条，真正的考试前反而没提醒。
     * 所以把每个类型最该被提醒的时刻放在最前面，节奏统一为「当天 / 前 1 天 / 前 2 天」连续三天：
     *   - EXAM / SCORE / RETEST / VOLUNTEER / ADMISSION：考前 1 天 + 当天
     *   - SIGNUP（报名类）/ TICKET（准考证类）：前 2 天 / 前 1 天 / 当天
     */
    @Value("${remind.nodes-by-type:EXAM:1,0;SCORE:1,0;RETEST:1,0;VOLUNTEER:1,0;ADMISSION:1,0;SIGNUP:2,1,0;TICKET:2,1,0}")
    private String nodesByTypeConfig;

    /**
     * 自建提醒的提醒节点，默认「考前 1 天 + 当天」。
     * 不做 30/7 天那么多点：一次性订阅每推一条都要用户当场授权，点太多授权体验很差。
     */
    @Value("${remind.custom-nodes:1,0}")
    private String customNodesConfig;

    public RemindJob(ExamMapper examMapper,
                     SubscriptionMapper subscriptionMapper,
                     PushLogMapper pushLogMapper,
                     UserExamMapper userExamMapper,
                     WxMessageService wxMessageService) {
        this.examMapper = examMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.pushLogMapper = pushLogMapper;
        this.userExamMapper = userExamMapper;
        this.wxMessageService = wxMessageService;
    }

    @Scheduled(cron = "${remind.cron}", zone = "Asia/Shanghai")
    public void run() {
        LocalDate today = LocalDate.now();

        // ---------- 官方节点：按节点类型取提醒点 ----------
        Set<Integer> fallbackNodes = parseNodes(nodesConfig, "30,7,1,0");
        Map<String, Set<Integer>> nodesByType = parseNodesByType(nodesByTypeConfig);

        for (Exam exam : examMapper.selectList(new QueryWrapper<Exam>().eq("status", 1))) {
            long days = ChronoUnit.DAYS.between(today, exam.getExamDate());
            Set<Integer> nodes = resolveNodes(nodesByType, exam.getNodeType(), fallbackNodes);
            if (nodes.contains((int) days)) {
                String nodeName = days == 0L ? "DAY" : "D" + days;
                log.info("命中提醒节点 exam={} type={} node={}", exam.getName(), exam.getNodeType(), nodeName);
                pushExam(exam, days, nodeName, nodes);
            }
        }

        // ---------- 用户自建节点：默认只在考前 1 天与当天提醒 ----------
        Set<Integer> customNodes = parseNodes(customNodesConfig, "1,0");
        for (UserExam ue : userExamMapper.selectList(new QueryWrapper<UserExam>()
                .eq("status", 1)
                .ge("exam_date", today))) {
            long days = ChronoUnit.DAYS.between(today, ue.getExamDate());
            if (customNodes.contains((int) days)) {
                String nodeName = days == 0L ? "DAY" : "D" + days;
                log.info("命中自建提醒节点 userExam={} name={} node={}", ue.getId(), ue.getName(), nodeName);
                pushCustom(ue, days, nodeName, customNodes);
            }
        }
    }

    private Set<Integer> parseNodes(String config, String fallback) {
        Set<Integer> nodes = new HashSet<>();
        String src = (config == null || config.isBlank()) ? fallback : config;
        for (String s : src.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                nodes.add(Integer.parseInt(t));
            }
        }
        return nodes;
    }

    /** 解析 "EXAM:1,0;SIGNUP:3,1,0" 形式的按类型配置 */
    private Map<String, Set<Integer>> parseNodesByType(String config) {
        Map<String, Set<Integer>> map = new LinkedHashMap<>();
        if (config == null || config.isBlank()) {
            return map;
        }
        for (String part : config.split(";")) {
            int idx = part.indexOf(':');
            if (idx <= 0 || idx == part.length() - 1) {
                continue;
            }
            String type = part.substring(0, idx).trim().toUpperCase();
            Set<Integer> nodes = parseNodes(part.substring(idx + 1).trim(), "1,0");
            if (!type.isEmpty() && !nodes.isEmpty()) {
                map.put(type, nodes);
            }
        }
        return map;
    }

    /** 取该节点类型的提醒点：优先按类型配置，其次 default，最后兜底全局配置 */
    private Set<Integer> resolveNodes(Map<String, Set<Integer>> byType, String nodeType, Set<Integer> fallback) {
        String type = (nodeType == null || nodeType.isBlank()) ? "EXAM" : nodeType.trim().toUpperCase();
        Set<Integer> hit = byType.get(type);
        if (hit == null) {
            hit = byType.get("DEFAULT");
        }
        return hit == null ? fallback : hit;
    }

    /**
     * 该提醒日还有多少个「从今天到截止日」的提醒点（含今天）。
     * 推送额度优先保证靠近截止日的提醒：额度不足以覆盖后续所有提醒点时，
     * 宁可今天不推，把额度留给更关键的日子（订 1 次只推截止当天，订 2 次推前一天+当天…）。
     */
    private int requiredQuota(Set<Integer> nodes, long days) {
        int count = 0;
        for (Integer n : nodes) {
            if (n != null && n <= days) {
                count++;
            }
        }
        return count;
    }

    private void pushExam(Exam exam, long days, String nodeName, Set<Integer> nodes) {
        // 模板 ID 以后端 exam.template_id 为准，取不到才回落全局默认
        String tplId = (exam.getTemplateId() != null && !exam.getTemplateId().isBlank())
                ? exam.getTemplateId() : templateId;
        int required = requiredQuota(nodes, days);

        for (Subscription sub : subscriptionMapper.selectList(
                new QueryWrapper<Subscription>()
                        .eq("exam_id", exam.getId())
                        .gt("remain_times", 0))) {

            if (sub.getRemainTimes() < required) {
                // 额度不够覆盖到截止日的全部提醒点，留给更关键的日子
                log.info("额度保留跳过 openid={} remain={} required={} node={}",
                        sub.getOpenid(), sub.getRemainTimes(), required, nodeName);
                continue;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            // ⚠️ 这几个 key 必须与微信后台模板字段一一对应，否则报 47003
            data.put("thing1", Map.of("value", truncate(exam.getName(), 20)));
            data.put("time2", Map.of("value", exam.getExamDate().atTime(9, 0).format(CN_DATETIME)));
            data.put("number3", Map.of("value", String.valueOf(days)));
            data.put("thing4", Map.of("value", truncate(nodeTip(exam.getNodeType(), days), 20)));

            int errcode = wxMessageService.send(
                    sub.getOpenid(), tplId,
                    "pages/index/index?examId=" + exam.getId(),
                    data, exam.getAppid());

            PushLog pl = new PushLog();
            pl.setOpenid(sub.getOpenid());
            pl.setExamId(exam.getId());
            pl.setNodeName(nodeName);

            if (errcode == 0) {
                sub.setRemainTimes(sub.getRemainTimes() - 1);
                subscriptionMapper.updateById(sub);
                pl.setResult("OK");
            } else if (errcode == 43101) { // 用户拒收：额度直接清零，别再生效
                sub.setRemainTimes(0);
                subscriptionMapper.updateById(sub);
                pl.setResult("FAIL");
                pl.setErrMsg("用户拒收 43101");
            } else {
                pl.setResult("FAIL");
                pl.setErrMsg("errcode=" + errcode);
            }
            pushLogMapper.insert(pl);
        }
    }

    /**
     * 自建提醒推送。exam_id 用命名空间 id（UserExam.ID_BASE + user_exam.id），
     * 模板取用户自己那条（建库时已写入全局默认），跳回首页并定位高亮该节点。
     */
    private void pushCustom(UserExam ue, long days, String nodeName, Set<Integer> nodes) {
        long publicId = UserExam.toPublicId(ue.getId());
        String tplId = (ue.getTemplateId() != null && !ue.getTemplateId().isBlank())
                ? ue.getTemplateId() : templateId;
        int required = requiredQuota(nodes, days);

        for (Subscription sub : subscriptionMapper.selectList(
                new QueryWrapper<Subscription>()
                        .eq("exam_id", publicId)
                        .gt("remain_times", 0))) {

            if (sub.getRemainTimes() < required) {
                log.info("额度保留跳过 openid={} remain={} required={} node={}",
                        sub.getOpenid(), sub.getRemainTimes(), required, nodeName);
                continue;
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("thing1", Map.of("value", truncate(ue.getName(), 20)));
            data.put("time2", Map.of("value", ue.getExamDate().atTime(9, 0).format(CN_DATETIME)));
            data.put("number3", Map.of("value", String.valueOf(days)));
            data.put("thing4", Map.of("value", truncate(nodeTip("CUSTOM", days), 20)));

            int errcode = wxMessageService.send(
                    sub.getOpenid(), tplId,
                    "pages/index/index?examId=" + publicId,
                    data, ue.getAppid());

            PushLog pl = new PushLog();
            pl.setOpenid(sub.getOpenid());
            pl.setExamId(publicId);
            pl.setNodeName(nodeName);

            if (errcode == 0) {
                sub.setRemainTimes(sub.getRemainTimes() - 1);
                subscriptionMapper.updateById(sub);
                pl.setResult("OK");
            } else if (errcode == 43101) { // 用户拒收：额度清零
                sub.setRemainTimes(0);
                subscriptionMapper.updateById(sub);
                pl.setResult("FAIL");
                pl.setErrMsg("用户拒收 43101");
            } else {
                pl.setResult("FAIL");
                pl.setErrMsg("errcode=" + errcode);
            }
            pushLogMapper.insert(pl);
        }
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String nodeTip(String nodeType, long days) {
        String type = nodeType == null ? "EXAM" : nodeType.trim().toUpperCase();
        switch (type) {
            case "SIGNUP":
                return days == 0L ? "今天是报名关键日，别错过！记得再订阅"
                        : "距报名节点还有" + days + "天，别错过时间";
            case "TICKET":
                return days == 0L ? "今天起可打印准考证，记得再订阅"
                        : "距准考证打印还有" + days + "天";
            case "SCORE":
                return days == 0L ? "成绩/分数线可能今天公布，记得再订阅"
                        : "距成绩公布约" + days + "天，记得再订阅";
            case "RETEST":
                return days == 0L ? "今天复试/面试，加油！记得再订阅"
                        : "距复试还有" + days + "天，加油";
            case "CUSTOM":
                return days == 0L ? "今天就是你设的提醒日，记得再订阅"
                        : "距你设置的日子还有" + days + "天，记得再订阅";
            case "EXAM":
            default:
                return days == 0L ? "今天考试，加油！记得再订阅"
                        : "距考试还有" + days + "天，点进小程序再订阅";
        }
    }
}
