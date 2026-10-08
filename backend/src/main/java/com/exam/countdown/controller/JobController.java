package com.exam.countdown.controller;

import com.exam.countdown.job.RemindJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 手动 / 外部触发提醒任务（云托管定时任务可配这个地址）。
 * 需要带 token，token 由环境变量 REMIND_JOB_TOKEN 注入。
 */
@RestController
@RequestMapping("/api/job")
public class JobController {

    private final RemindJob remindJob;

    @Value("${remind.job-token:change-me}")
    private String jobToken;

    public JobController(RemindJob remindJob) {
        this.remindJob = remindJob;
    }

    @GetMapping("/remind")
    public Map<String, Object> remind(@RequestParam(required = false) String token) {
        if (!jobToken.equals(token)) {
            return Map.of("ok", false, "msg", "token 无效");
        }
        remindJob.run();
        return Map.of("ok", true);
    }
}
