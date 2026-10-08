package com.exam.countdown.dto;

import lombok.Generated;

public class SubscribeReq {
   private String openid;
   private Long examId;

   @Generated
   public String getOpenid() {
      return this.openid;
   }

   @Generated
   public Long getExamId() {
      return this.examId;
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
   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof SubscribeReq other)) {
         return false;
      } else if (!other.canEqual(this)) {
         return false;
      } else {
         Object this$examId = this.getExamId();
         Object other$examId = other.getExamId();
         if (this$examId == null ? other$examId == null : this$examId.equals(other$examId)) {
            Object this$openid = this.getOpenid();
            Object other$openid = other.getOpenid();
            return this$openid == null ? other$openid == null : this$openid.equals(other$openid);
         } else {
            return false;
         }
      }
   }

   @Generated
   protected boolean canEqual(final Object other) {
      return other instanceof SubscribeReq;
   }

   @Generated
   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      Object $examId = this.getExamId();
      result = result * 59 + ($examId == null ? 43 : $examId.hashCode());
      Object $openid = this.getOpenid();
      return result * 59 + ($openid == null ? 43 : $openid.hashCode());
   }

   @Generated
   @Override
   public String toString() {
      return "SubscribeReq(openid=" + this.getOpenid() + ", examId=" + this.getExamId() + ")";
   }
}
