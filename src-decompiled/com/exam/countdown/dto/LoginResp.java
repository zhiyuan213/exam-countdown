package com.exam.countdown.dto;

import lombok.Generated;

public class LoginResp {
   private String openid;

   public LoginResp(String openid) {
      this.openid = openid;
   }

   @Generated
   public String getOpenid() {
      return this.openid;
   }

   @Generated
   public void setOpenid(final String openid) {
      this.openid = openid;
   }

   @Generated
   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof LoginResp other)) {
         return false;
      } else if (!other.canEqual(this)) {
         return false;
      } else {
         Object this$openid = this.getOpenid();
         Object other$openid = other.getOpenid();
         return this$openid == null ? other$openid == null : this$openid.equals(other$openid);
      }
   }

   @Generated
   protected boolean canEqual(final Object other) {
      return other instanceof LoginResp;
   }

   @Generated
   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      Object $openid = this.getOpenid();
      return result * 59 + ($openid == null ? 43 : $openid.hashCode());
   }

   @Generated
   @Override
   public String toString() {
      return "LoginResp(openid=" + this.getOpenid() + ")";
   }
}
