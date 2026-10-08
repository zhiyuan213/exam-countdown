package com.exam.countdown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("exam")
public class Exam {
   @TableId(
      type = IdType.AUTO
   )
   private Long id;
   private String name;
   private LocalDate examDate;
   private LocalDate signupDate;
   private LocalDate ticketDate;
   private Integer status;
   private String appKey;
   private String appid;
   private String templateId;
   private String groupCode;
   private String groupName;
   private String nodeType;
   private Integer sort;

   public String getGroupCode() {
      return this.groupCode;
   }

   public void setGroupCode(String groupCode) {
      this.groupCode = groupCode;
   }

   public String getGroupName() {
      return this.groupName;
   }

   public void setGroupName(String groupName) {
      this.groupName = groupName;
   }

   public String getNodeType() {
      return this.nodeType;
   }

   public void setNodeType(String nodeType) {
      this.nodeType = nodeType;
   }

   public Integer getSort() {
      return this.sort;
   }

   public void setSort(Integer sort) {
      this.sort = sort;
   }

   public Long getId() {
      return this.id;
   }

   public void setId(Long id) {
      this.id = id;
   }

   public String getName() {
      return this.name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public LocalDate getExamDate() {
      return this.examDate;
   }

   public void setExamDate(LocalDate examDate) {
      this.examDate = examDate;
   }

   public LocalDate getSignupDate() {
      return this.signupDate;
   }

   public void setSignupDate(LocalDate signupDate) {
      this.signupDate = signupDate;
   }

   public LocalDate getTicketDate() {
      return this.ticketDate;
   }

   public void setTicketDate(LocalDate ticketDate) {
      this.ticketDate = ticketDate;
   }

   public Integer getStatus() {
      return this.status;
   }

   public void setStatus(Integer status) {
      this.status = status;
   }

   public String getAppKey() {
      return this.appKey;
   }

   public void setAppKey(String appKey) {
      this.appKey = appKey;
   }

   public String getAppid() {
      return this.appid;
   }

   public void setAppid(String appid) {
      this.appid = appid;
   }

   public String getTemplateId() {
      return this.templateId;
   }

   public void setTemplateId(String templateId) {
      this.templateId = templateId;
   }
}
