package com.exam.countdown.controller;

import com.exam.countdown.job.RemindJob;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/job"})
public class JobController {
   private final RemindJob remindJob;
   @Value("${remind.job-token:change-me}")
   private String jobToken;

   public JobController(RemindJob remindJob) {
      this.remindJob = remindJob;
   }

   @GetMapping({"/remind"})
   public Map<String, Object> remind(@RequestParam(required = false) String token) {
      if (!this.jobToken.equals(token)) {
         return Map.of("ok", false, "msg", "token 无效");
      } else {
         this.remindJob.run();
         return Map.of("ok", true);
      }
   }
}
