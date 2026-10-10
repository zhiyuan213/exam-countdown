package com.exam.countdown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户提醒额度。
 *
 * limit = freeQuota + paidQuota，含义是「可同时管理的未过期提醒数」；
 * 实际占用按 user_exam 中 status=1 且未过期的行数实时统计，删除或过期即释放。
 * paidQuota 由虚拟支付回调累加（二期），一期只读不改。
 */
@Data
@TableName("user_quota")
public class UserQuota {

    /** openid 既是主键，不能自增 */
    @TableId(type = IdType.INPUT)
    private String openid;

    /** 免费额度，默认 3 */
    private Integer freeQuota;

    /** 已购额度，累加 */
    private Integer paidQuota;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
