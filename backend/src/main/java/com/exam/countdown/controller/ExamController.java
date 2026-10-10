package com.exam.countdown.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exam.countdown.entity.Exam;
import com.exam.countdown.entity.UserExam;
import com.exam.countdown.mapper.ExamMapper;
import com.exam.countdown.mapper.UserExamMapper;
import com.exam.countdown.support.OpenidSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上架节点列表，按日期升序。
 * 请求头 X-EXAM-APP 指定小程序标识（kaoyan / jiaozhi）；
 * 不传、或传 main/all，则返回全部官方节点（当前线上就是这个模式）。
 *
 * 另外：如果请求带上了 openid（云托管注入），会把该用户自己加的提醒一并返回，
 * 分组为「自定义提醒」（groupCode=custom，isCustom=true，id 见 UserExam.ID_BASE）。
 */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamMapper examMapper;
    private final UserExamMapper userExamMapper;

    public ExamController(ExamMapper examMapper, UserExamMapper userExamMapper) {
        this.examMapper = examMapper;
        this.userExamMapper = userExamMapper;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestHeader(value = "X-EXAM-APP", required = false) String appKey,
            @RequestHeader(value = "X-WX-FROM-OPENID", required = false) String fromOpenid,
            @RequestHeader(value = "X-WX-OPENID", required = false) String cloudOpenid) {

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

        List<Map<String, Object>> out = new ArrayList<>();
        for (Exam e : examMapper.selectList(qw)) {
            out.add(officialItem(e));
        }

        String openid = OpenidSupport.resolve(fromOpenid, cloudOpenid, null);
        if (openid != null) {
            List<UserExam> mine = userExamMapper.selectList(new QueryWrapper<UserExam>()
                    .eq("openid", openid)
                    .eq("status", 1)
                    .orderByAsc("exam_date"));
            for (UserExam ue : mine) {
                out.add(customItem(ue));
            }
        }
        return out;
    }

    /** 官方节点：字段与历史版本一致，仅多一个 isCustom=false */
    private Map<String, Object> officialItem(Exam e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("name", e.getName());
        m.put("examDate", e.getExamDate());
        m.put("templateId", e.getTemplateId());
        m.put("nodeType", e.getNodeType());
        m.put("groupCode", e.getGroupCode());
        m.put("groupName", e.getGroupName());
        m.put("sort", e.getSort());
        m.put("isCustom", false);
        return m;
    }

    /** 自建节点：id 走命名空间，统一挂到「自定义提醒」分组 */
    private Map<String, Object> customItem(UserExam ue) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", UserExam.toPublicId(ue.getId()));
        m.put("name", ue.getName());
        m.put("examDate", ue.getExamDate());
        m.put("templateId", ue.getTemplateId());
        m.put("nodeType", "CUSTOM");
        m.put("groupCode", "custom");
        m.put("groupName", "自定义提醒");
        m.put("sort", 0);
        m.put("isCustom", true);
        return m;
    }
}
