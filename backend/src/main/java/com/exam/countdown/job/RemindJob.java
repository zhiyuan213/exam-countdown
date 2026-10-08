package com.exam.countdown.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exam.countdown.entity.Exam;
import com.exam.countdown.entity.PushLog;
import com.exam.countdown.entity.Subscription;
import com.exam.countdown.mapper.ExamMapper;
import com.exam.countdown.mapper.PushLogMapper;
import com.exam.countdown.mapper.SubscriptionMapper;
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
    private final WxMessageService wxMessageService;

    /** 全局默认模板 ID；exam.template_id 为空时用它 */
    @Value("${wx.template-id}")
    private String templateId;

    /** 提醒节点：考前 30/7/1 天及当天 */
    @Value("${remind.nodes:30,7,1,0}")
    private String nodesConfig;

    public RemindJob(ExamMapper examMapper,
                     SubscriptionMapper subscriptionMapper,
                     PushLogMapper pushLogMapper,
                     WxMessageService wxMessageService) {
        this.examMapper = examMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.pushLogMapper = pushLogMapper;
        this.wxMessageService = wxMessageService;
    }

    @Scheduled(cron = "${remind.cron}", zone = "Asia/Shanghai")
    public void run() {
        LocalDate today = LocalDate.now();
        Set<Integer> nodes = new HashSet<>();
        for (String s : nodesConfig.split(",")) {
            nodes.add(Integer.parseInt(s.trim()));
        }

        for (Exam exam : examMapper.selectList(new QueryWrapper<Exam>().eq("status", 1))) {
            long days = ChronoUnit.DAYS.between(today, exam.getExamDate());
            if (nodes.contains((int) days)) {
                String nodeName = days == 0L ? "DAY" : "D" + days;
                log.info("命中提醒节点 exam={} node={}", exam.getName(), nodeName);
                pushExam(exam, days, nodeName);
            }
        }
    }

    private void pushExam(Exam exam, long days, String nodeName) {
        // 模板 ID 以后端 exam.template_id 为准，取不到才回落全局默认
        String tplId = (exam.getTemplateId() != null && !exam.getTemplateId().isBlank())
                ? exam.getTemplateId() : templateId;

        for (Subscription sub : subscriptionMapper.selectList(
                new QueryWrapper<Subscription>()
                        .eq("exam_id", exam.getId())
                        .gt("remain_times", 0))) {

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
            case "EXAM":
            default:
                return days == 0L ? "今天考试，加油！记得再订阅"
                        : "距考试还有" + days + "天，点进小程序再订阅";
        }
    }
}
