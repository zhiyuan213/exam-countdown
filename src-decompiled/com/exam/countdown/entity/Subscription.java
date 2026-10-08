package com.exam.countdown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Generated;

@TableName("subscription")
public class Subscription {
   @TableId(
      type = IdType.AUTO
   )
   private Long id;
   private String openid;
   private Long examId;
   private String templateId;
   private Integer remainTimes;
   private LocalDateTime createdAt;

   @Generated
   public Long getId() {
      return this.id;
   }

   @Generated
   public String getOpenid() {
      return this.openid;
   }

   @Generated
   public Long getExamId() {
      return this.examId;
   }

   @Generated
   public String getTemplateId() {
      return this.templateId;
   }

   @Generated
   public Integer getRemainTimes() {
      return this.remainTimes;
   }

   @Generated
   public LocalDateTime getCreatedAt() {
      return this.createdAt;
   }

   @Generated
   public void setId(final Long id) {
      this.id = id;
   }

   @Generated
   public void setOpenid(final String openid) {
      this.openid = openid;
   }

   @Generated
   public void setExamId(final Long examId) {
      this.examId = examId;
   }

   @Generated
   public void setTemplateId(final String templateId) {
      this.templateId = templateId;
   }

   @Generated
   public void setRemainTimes(final Integer remainTimes) {
      this.remainTimes = remainTimes;
   }

   @Generated
   public void setCreatedAt(final LocalDateTime createdAt) {
      this.createdAt = createdAt;
   }

   @Generated
   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof Subscription other)) {
         return false;
      } else if (!other.canEqual(this)) {
         return false;
      } else {
         Object this$id = this.getId();
         Object other$id = other.getId();
         if (this$id == null ? other$id == null : this$id.equals(other$id)) {
            Object this$examId = this.getExamId();
            Object other$examId = other.getExamId();
            if (this$examId == null ? other$examId == null : this$examId.equals(other$examId)) {
               Object this$remainTimes = this.getRemainTimes();
               Object other$remainTimes = other.getRemainTimes();
               if (this$remainTimes == null ? other$remainTimes == null : this$remainTimes.equals(other$remainTimes)) {
                  Object this$openid = this.getOpenid();
                  Object other$openid = other.getOpenid();
                  if (this$openid == null ? other$openid == null : this$openid.equals(other$openid)) {
                     Object this$templateId = this.getTemplateId();
                     Object other$templateId = other.getTemplateId();
                     if (this$templateId == null ? other$templateId == null : this$templateId.equals(other$templateId)) {
                        Object this$createdAt = this.getCreatedAt();
                        Object other$createdAt = other.getCreatedAt();
                        return this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt);
                     } else {
                        return false;
                     }
                  } else {
                     return false;
                  }
               } else {
                  return false;
               }
            } else {
               return false;
            }
         } else {
            return false;
         }
      }
   }

   @Generated
   protected boolean canEqual(final Object other) {
      return other instanceof Subscription;
   }

   @Generated
   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      Object $id = this.getId();
      result = result * 59 + ($id == null ? 43 : $id.hashCode());
      Object $examId = this.getExamId();
      result = result * 59 + ($examId == null ? 43 : $examId.hashCode());
      Object $remainTimes = this.getRemainTimes();
      result = result * 59 + ($remainTimes == null ? 43 : $remainTimes.hashCode());
      Object $openid = this.getOpenid();
      result = result * 59 + ($openid == null ? 43 : $openid.hashCode());
      Object $templateId = this.getTemplateId();
      result = result * 59 + ($templateId == null ? 43 : $templateId.hashCode());
      Object $createdAt = this.getCreatedAt();
      return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
   }

   @Generated
   @Override
   public String toString() {
      return "Subscription(id="
         + this.getId()
         + ", openid="
         + this.getOpenid()
         + ", examId="
         + this.getExamId()
         + ", templateId="
         + this.getTemplateId()
         + ", remainTimes="
         + this.getRemainTimes()
         + ", createdAt="
         + this.getCreatedAt()
         + ")";
   }
}
