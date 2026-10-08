package com.exam.countdown.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exam.countdown.entity.Exam;
import com.exam.countdown.mapper.ExamMapper;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/exams"})
public class ExamController {
   private final ExamMapper examMapper;

   public ExamController(ExamMapper examMapper) {
      this.examMapper = examMapper;
   }

   @GetMapping
   public List<Exam> list(@RequestHeader(value = "X-EXAM-APP",required = false) String appKey) {
      QueryWrapper<Exam> qw = (QueryWrapper<Exam>)((QueryWrapper)new QueryWrapper().eq("status", 1)).orderByAsc("exam_date");
      String key = appKey == null ? null : appKey.trim();
      boolean isGeneric = key == null || key.isBlank() || "main".equalsIgnoreCase(key) || "all".equalsIgnoreCase(key);
      if (!isGeneric) {
         qw.eq("app_key", key);
      }

      return this.examMapper.selectList(qw);
   }
}
