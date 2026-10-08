package com.exam.countdown.job;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exam.countdown.entity.Exam;
import com.exam.countdown.entity.PushLog;
import com.exam.countdown.entity.Subscription;
import com.exam.countdown.mapper.ExamMapper;
import com.exam.countdown.mapper.PushLogMapper;
import com.exam.countdown.mapper.SubscriptionMapper;
import com.exam.countdown.service.WxMessageService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RemindJob {
   private static final Logger log = LoggerFactory.getLogger(RemindJob.class);
   private static final DateTimeFormatter CN_DATETIME = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm");
   private final ExamMapper examMapper;
   private final SubscriptionMapper subscriptionMapper;
   private final PushLogMapper pushLogMapper;
   private final WxMessageService wxMessageService;
   @Value("${wx.template-id}")
   private String templateId;
   @Value("${remind.nodes:30,7,1,0}")
   private String nodesConfig;

   public RemindJob(ExamMapper examMapper, SubscriptionMapper subscriptionMapper, PushLogMapper pushLogMapper, WxMessageService wxMessageService) {
      this.examMapper = examMapper;
      this.subscriptionMapper = subscriptionMapper;
      this.pushLogMapper = pushLogMapper;
      this.wxMessageService = wxMessageService;
   }

   @Scheduled(
      cron = "${remind.cron}",
      zone = "Asia/Shanghai"
   )
   public void run() {
      LocalDate today = LocalDate.now();
      Set<Integer> nodes = new HashSet<>();

      for (String s : this.nodesConfig.split(",")) {
         nodes.add(Integer.parseInt(s.trim()));
      }

      for (Exam exam : this.examMapper.selectList((Wrapper)new QueryWrapper().eq("status", 1))) {
         long days = ChronoUnit.DAYS.between(today, exam.getExamDate());
         if (nodes.contains((int)days)) {
            String nodeName = days == 0L ? "DAY" : "D" + days;
            log.info("命中提醒节点 exam={} node={}", exam.getName(), nodeName);
            this.pushExam(exam, days, nodeName);
         }
      }
   }

   private void pushExam(Exam exam, long days, String nodeName) {
      String tplId = exam.getTemplateId() != null && !exam.getTemplateId().isBlank() ? exam.getTemplateId() : this.templateId;

      for (Subscription sub : this.subscriptionMapper.selectList((Wrapper)((QueryWrapper)new QueryWrapper().eq("exam_id", exam.getId())).gt("remain_times", 0))) {
         Map<String, Object> data = new LinkedHashMap<>();
         data.put("thing1", Map.of("value", this.truncate(exam.getName(), 20)));
         data.put("time2", Map.of("value", exam.getExamDate().atTime(9, 0).format(CN_DATETIME)));
         data.put("number3", Map.of("value", String.valueOf(days)));
         data.put("thing4", Map.of("value", this.truncate(this.nodeTip(exam.getNodeType(), days), 20)));
         int errcode = this.wxMessageService.send(sub.getOpenid(), tplId, "pages/index/index?examId=" + exam.getId(), data, exam.getAppid());
         PushLog pl = new PushLog();
         pl.setOpenid(sub.getOpenid());
         pl.setExamId(exam.getId());
         pl.setNodeName(nodeName);
         if (errcode == 0) {
            sub.setRemainTimes(sub.getRemainTimes() - 1);
            this.subscriptionMapper.updateById(sub);
            pl.setResult("OK");
         } else if (errcode == 43101) {
            sub.setRemainTimes(0);
            this.subscriptionMapper.updateById(sub);
            pl.setResult("FAIL");
            pl.setErrMsg("用户拒收 43101");
         } else {
            pl.setResult("FAIL");
            pl.setErrMsg("errcode=" + errcode);
         }

         this.pushLogMapper.insert(pl);
      }
   }

   private String truncate(String s, int max) {
      return s.length() <= max ? s : s.substring(0, max);
   }

   private String nodeTip(String nodeType, long days) {
      String type = nodeType == null ? "EXAM" : nodeType.trim().toUpperCase();
      switch (type) {
         case "SIGNUP":
            return days == 0L ? "今天是报名关键日，别错过！记得再订阅" : "距报名节点还有" + days + "天，别错过时间";
         case "TICKET":
            return days == 0L ? "今天起可打印准考证，记得再订阅" : "距准考证打印还有" + days + "天";
         case "SCORE":
            return days == 0L ? "成绩/分数线可能今天公布，记得再订阅" : "距成绩公布约" + days + "天，记得再订阅";
         case "RETEST":
            return days == 0L ? "今天复试/面试，加油！记得再订阅" : "距复试还有" + days + "天，加油";
         case "EXAM":
         default:
            return days == 0L ? "今天考试，加油！记得再订阅" : "距考试还有" + days + "天，点进小程序再订阅";
      }
   }
}
