package com.exam.countdown.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exam.countdown.entity.Exam;
import com.exam.countdown.mapper.ExamMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamMapper examMapper;

    public ExamController(ExamMapper examMapper) {
        this.examMapper = examMapper;
    }

    /**
     * 上架节点列表，按日期升序。
     * 请求头 X-EXAM-APP 指定小程序标识（kaoyan / jiaozhi）；
     * 不传、或传 main/all，则返回全部（当前线上就是这个模式）。
     */
    @GetMapping
    public List<Exam> list(@RequestHeader(value = "X-EXAM-APP", required = false) String appKey) {
        QueryWrapper<Exam> qw = new QueryWrapper<Exam>()
                .eq("status", 1)
                .orderByAsc("exam_date");

        String key = appKey == null ? null : appKey.trim();
        boolean isGeneric = key == null || key.isBlank()
                || "main".equalsIgnoreCase(key)
                || "all".equalsIgnoreCase(key);
        if (!isGeneric) {
            qw.eq("app_key", key);
        }
        return examMapper.selectList(qw);
    }
}
