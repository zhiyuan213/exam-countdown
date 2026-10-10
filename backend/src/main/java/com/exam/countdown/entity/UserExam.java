package com.exam.countdown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户自建提醒（前端「自定义提醒」分组）。
 *
 * 与官方节点表 exam 分开存：exam 由运营配置，本表由用户自己添加。
 *
 * ID 命名空间约定（重要）：
 *   - 官方节点 id 都 &lt; ID_BASE，直接暴露给前端；
 *   - 自建节点对前端暴露的 id = ID_BASE + 本表 id；
 *   - subscription.exam_id 沿用同一套 id 规则，因此一张订阅表可以同时装两类节点，
 *     也避免了「自建 id=1 与官方 id=1 撞车」导致推送串号。
 * 详见 {@link #toPublicId(Long)} / {@link #toLocalId(Long)}。
 */
@Data
@TableName("user_exam")
public class UserExam {

    /** 自建节点 id 的起始值，官方节点 id 均在 10 亿以下 */
    public static final long ID_BASE = 1_000_000_000L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建者 openid */
    private String openid;

    /** 提醒名称，≤20 字（订阅消息 thing 字段限制） */
    private String name;

    /** 目标日期 */
    private LocalDate examDate;

    /** 订阅消息模板 ID（空则用全局默认） */
    private String templateId;

    /** 所属小程序 AppID（矩阵化预留） */
    private String appid;

    /** 1 有效 0 已删除 */
    private Integer status;

    private LocalDateTime createdAt;

    /** 本表 id → 对前端暴露的 id */
    public static long toPublicId(Long id) {
        return ID_BASE + (id == null ? 0L : id);
    }

    /** 前端传来的 id → 本表 id；不是自建节点则返回 null */
    public static Long toLocalId(Long publicId) {
        if (publicId == null || publicId < ID_BASE) {
            return null;
        }
        return publicId - ID_BASE;
    }

    /** 判断前端传来的 id 是否属于自建节点 */
    public static boolean isCustomId(Long publicId) {
        return publicId != null && publicId >= ID_BASE;
    }
}
