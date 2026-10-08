package com.exam.countdown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表。openid 唯一。
 */
@Data
@TableName("wx_user")
public class WxUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;

    private LocalDateTime createdAt;
}
