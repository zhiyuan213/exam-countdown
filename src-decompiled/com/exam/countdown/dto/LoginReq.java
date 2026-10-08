package com.exam.countdown.dto;

import lombok.Generated;

public class LoginReq {
   private String code;

   @Generated
   public String getCode() {
      return this.code;
   }

   @Generated
   public void setCode(final String code) {
      this.code = code;
   }

   @Generated
   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof LoginReq other)) {
         return false;
      } else if (!other.canEqual(this)) {
         return false;
      } else {
         Object this$code = this.getCode();
         Object other$code = other.getCode();
         return this$code == null ? other$code == null : this$code.equals(other$code);
      }
   }

   @Generated
   protected boolean canEqual(final Object other) {
      return other instanceof LoginReq;
   }

   @Generated
   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      Object $code = this.getCode();
      return result * 59 + ($code == null ? 43 : $code.hashCode());
   }

   @Generated
   @Override
   public String toString() {
      return "LoginReq(code=" + this.getCode() + ")";
   }
}
