package com.exam.countdown.support;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.exam.countdown.entity.Exam;
import com.exam.countdown.entity.UserExam;
import com.exam.countdown.mapper.ExamMapper;
import com.exam.countdown.mapper.UserExamMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 失效订阅消息模板 ID 的启动自愈迁移。
 *
 * 背景（2026-10-13 实测踩坑）：订阅消息模板是挂在「添加模板时的服务类目」下的。
 * 微信规则 —— **修改类目，原类目下的模板会被删除**（新增类目则存量模板不受影响）。
 * 本项目为过审把类目从「工具」改为「工具-备忘录」，旧类目下的「考试提醒」模板被
 * 连带删除，模板 ID 立即失效：用户端 {@code wx.requestSubscribeMessage} 每次都失败，
 * 一个节点也订不上（表现为「设置里开关是开的，但模板列表空着」）。
 *
 * 但模板 ID 在库里是**快照**：
 *   - {@code exam.template_id}      —— 官方节点，种子写入后再不会更新
 *                                   （schema.sql 是 INSERT IGNORE，改种子对已有库无效）
 *   - {@code user_exam.template_id} —— 用户建自建提醒时抄下的当时的全局默认值
 * 所以光在 mp 后台重新添加模板、改环境变量是**不够的**：库里那些行仍是失效的旧 ID，
 * 推送时按「行优先、全局兜底」取值，就会继续拿旧 ID 发消息（40037 invalid template_id）。
 *
 * 这里把「改写失效 ID」做成每次启动都执行、且幂等的动作：
 * 只动 {@code template_id} 恰好等于历史失效 ID 的行，改写成当前配置的全局模板；
 * 若全局模板未配置则跳过（宁可不动，也不要写坏）。以后再换模板，只需把失效 ID
 * 追加进 {@link #RETIRED} 并把环境变量 {@code WX_TEMPLATE_ID} 指向新 ID 即可。
 */
@Component
public class TemplateIdMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TemplateIdMigrator.class);

    /**
     * 历史失效的模板 ID 清单（改类目时被微信连带删除的那批）。
     * 新增失效 ID 时直接往这里追加即可，迁移幂等，重复执行无副作用。
     */
    private static final String[] RETIRED = {
            // 2026-10-13 类目「工具」→「工具-备忘录」后被删除的旧模板
            "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    };

    private final ExamMapper examMapper;
    private final UserExamMapper userExamMapper;

    /** 当前有效的全局默认模板 ID（WX_TEMPLATE_ID） */
    @Value("${wx.template-id:}")
    private String currentTemplateId;

    public TemplateIdMigrator(ExamMapper examMapper, UserExamMapper userExamMapper) {
        this.examMapper = examMapper;
        this.userExamMapper = userExamMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (currentTemplateId == null || currentTemplateId.isBlank()) {
            // 没有可用的新模板，什么都别改：改坏比不改更糟
            log.warn("WX_TEMPLATE_ID 未配置，跳过失效模板 ID 迁移；推送将没有可用的模板 ID");
            return;
        }
        String cur = currentTemplateId.trim();
        for (String old : RETIRED) {
            if (old == null || old.isBlank() || old.equals(cur)) {
                continue;
            }
            try {
                int official = examMapper.update(null,
                        new UpdateWrapper<Exam>().set("template_id", cur).eq("template_id", old));
                int custom = userExamMapper.update(null,
                        new UpdateWrapper<UserExam>().set("template_id", cur).eq("template_id", old));
                if (official + custom > 0) {
                    log.info("失效模板 ID 迁移完成: {} -> {}，exam {} 行 / user_exam {} 行",
                            old, cur, official, custom);
                }
            } catch (Exception e) {
                // 迁移失败不能影响服务启动（推送主流程另有日志可查）
                log.error("失效模板 ID 迁移失败 old={}: {}", old, e.toString());
            }
        }
    }
}
