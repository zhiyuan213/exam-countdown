package com.exam.countdown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

/**
 * 考试节点表。一行 = 一个「节点」（考研报名 / 考研准考证打印 / 考研初试 ...），
 * 用 group_code 把同一场考试的多个节点聚成一组，前端按组展示。
 */
@TableName("exam")
public class Exam {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 节点名称，≤20 字（订阅消息 thing 字段限制） */
    private String name;

    /** 该节点的目标日期 */
    private LocalDate examDate;

    /** 旧字段，兼容保留 */
    private LocalDate signupDate;

    /** 旧字段，兼容保留 */
    private LocalDate ticketDate;

    /** 1 上架 0 下架 */
    private Integer status;

    /** 矩阵化：所属小程序标识 kaoyan / jiaozhi */
    private String appKey;

    /** 矩阵化：所属小程序 AppID */
    private String appid;

    /** 矩阵化：该小程序的订阅消息模板 ID（空则用全局默认） */
    private String templateId;

    /** 考试大类标识 kaoyan / jiaozhi / cet / gongkao */
    private String groupCode;

    /** 大类显示名（考研 / 教资 ...） */
    private String groupName;

    /** 节点类型 SIGNUP / TICKET / EXAM / SCORE / RETEST */
    private String nodeType;

    /** 同组内展示顺序 */
    private Integer sort;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getExamDate() {
        return examDate;
    }

    public void setExamDate(LocalDate examDate) {
        this.examDate = examDate;
    }

    public LocalDate getSignupDate() {
        return signupDate;
    }

    public void setSignupDate(LocalDate signupDate) {
        this.signupDate = signupDate;
    }

    public LocalDate getTicketDate() {
        return ticketDate;
    }

    public void setTicketDate(LocalDate ticketDate) {
        this.ticketDate = ticketDate;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAppid() {
        return appid;
    }

    public void setAppid(String appid) {
        this.appid = appid;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
