package com.exam.countdown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Generated;

@TableName("wx_user")
public class WxUser {
   @TableId(
      type = IdType.AUTO
   )
   private Long id;
   private String openid;
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
   public void setCreatedAt(final LocalDateTime createdAt) {
      this.createdAt = createdAt;
   }

   @Generated
   @Override
   public boolean equals(final Object o) {
      if (o == this) {
         return true;
      } else if (!(o instanceof WxUser other)) {
         return false;
      } else if (!other.canEqual(this)) {
         return false;
      } else {
         Object this$id = this.getId();
         Object other$id = other.getId();
         if (this$id == null ? other$id == null : this$id.equals(other$id)) {
            Object this$openid = this.getOpenid();
            Object other$openid = other.getOpenid();
            if (this$openid == null ? other$openid == null : this$openid.equals(other$openid)) {
               Object this$createdAt = this.getCreatedAt();
               Object other$createdAt = other.getCreatedAt();
               return this$createdAt == null ? other$createdAt == null : this$createdAt.equals(other$createdAt);
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
      return other instanceof WxUser;
   }

   @Generated
   @Override
   public int hashCode() {
      int PRIME = 59;
      int result = 1;
      Object $id = this.getId();
      result = result * 59 + ($id == null ? 43 : $id.hashCode());
      Object $openid = this.getOpenid();
      result = result * 59 + ($openid == null ? 43 : $openid.hashCode());
      Object $createdAt = this.getCreatedAt();
      return result * 59 + ($createdAt == null ? 43 : $createdAt.hashCode());
   }

   @Generated
   @Override
   public String toString() {
      return "WxUser(id=" + this.getId() + ", openid=" + this.getOpenid() + ", createdAt=" + this.getCreatedAt() + ")";
   }
}
