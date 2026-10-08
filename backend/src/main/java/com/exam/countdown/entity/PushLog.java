package com.exam.countdown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 推送日志。每次尝试推送都写一条（成功失败都写）。
 */
@Data
@TableName("push_log")
public class PushLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;

    private Long examId;

    /** 提醒节点: D30 / D7 / D1 / DAY */
    private String nodeName;

    /** OK / FAIL */
    private String result;

    private String errMsg;

    private LocalDateTime createdAt;
}
