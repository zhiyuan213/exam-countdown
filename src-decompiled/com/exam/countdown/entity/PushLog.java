package com.exam.countdown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Generated;

@TableName("push_log")
public class PushLog {
   @TableId(
      type = IdType.AUTO
   )
   private Long id;
   private String openid;
   private Long examId;
   private String nodeName;
   private String result;
   private String errMsg;
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
   public String getNodeName() {
      return this.nodeName;
   }

   @Generated
   public String getResult() {
      return this.result;
   }

   @Generated
   public String getErrMsg() {
      return this.errMsg;
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
   public void setNodeName(final String nodeName) {
      this.nodeName = nodeName;
   }

   @Generated
   public void setResult(final String result) {
      this.result = result;
   }

   @Generated
   public void setErrMsg(final String errMsg) {
      this.errMsg = errMsg;
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
      } else if (!(o instanceof PushLog other)) {
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
               Object this$openid = this.getOpenid();
               Object other$openid = other.getOpenid();
               if (this$openid == null ? other$openid == null : this$openid.equals(other$openid)) {
                  Object this$nodeName = this.getNodeName();
                  Object other$nodeName = other.getNodeName();
                  if (this$nodeName == null ? other$nodeName == null : this$nodeName.equals(other$nodeName)) {
                     Object this$result = this.getResult();
                     Object other$result = other.getResult();
                     if (this$result == null ? other$result == null : this$result.equals(other$result)) {
                        Object this$errMsg = this.getErrMsg();
                        Object other$errMsg = other.getErrMsg();
                        if (this$errMsg == null ? other$errMsg == null : this$errMsg.equals(other$errMsg)) {
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
         } else {
            return false;
         }
      }
   }

   @Generated
   protected boolean canEqual(final Object other) {
      return other instanceof PushLog;
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
      Object $openid = this.getOpenid();
      result = result * 59 + ($openid == null ? 43 : $openid.hashCode());
      Object $nodeName = this.getNodeName();
      result = result * 59 + ($nodeName == null ? 43 : $nodeName.hashCode());
      Object $result = this.getResult();
      result = result * 59 + ($result == null ? 43 : $result.hashCode());
      Object $errMsg = this.getErrMsg();
      result = result * 59 + ($errMsg == null ? 43 : $errMsg.hashCode());
      Object $createdAt = this.getCreatedAt();
      return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
   }

   @Generated
   @Override
   public String toString() {
      return "PushLog(id="
         + this.getId()
         + ", openid="
         + this.getOpenid()
         + ", examId="
         + this.getExamId()
         + ", nodeName="
         + this.getNodeName()
         + ", result="
         + this.getResult()
         + ", errMsg="
         + this.getErrMsg()
         + ", createdAt="
         + this.getCreatedAt()
         + ")";
   }
}
