package com.exam.countdown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订阅配额表。微信订阅消息「一次授权只能推一条」，
 * 所以每次用户点订阅就 +1 额度，推送成功扣 1。
 */
@Data
@TableName("subscription")
public class Subscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;

    private Long examId;

    /** 订阅消息模板 ID */
    private String templateId;

    /** 剩余可推送次数 */
    private Integer remainTimes;

    private LocalDateTime createdAt;
}
