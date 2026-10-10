package com.exam.countdown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 虚拟支付订单（二期）。
 *
 * 状态机：INIT（已下单未支付）-> PAID（已收到支付信息）-> DELIVERED（已发货，额度已到账）
 *         兜底：CLOSED（超时关单，一期不做，保留状态位）
 *
 * 主键就是下单时传给微信的 outTradeNo（业务订单号），微信发货推送靠它回捞订单。
 * wxOrderId 存微信侧平台单号（WeChatPayInfo.MchOrderNo），用于幂等去重。
 */
@Data
@TableName("pay_order")
public class PayOrder {

    /** 业务订单号（同时是主键），格式 T + yyyyMMddHHmmssSSS + 4 位随机 */
    @TableId(type = IdType.INPUT)
    private String outTradeNo;

    private String openid;

    /** 道具 ID（与 mp 后台「道具管理」里的道具 ID 一致） */
    private String productId;

    private String goodsName;

    /** 本单应发放的额度数（= 商品配额 × 数量），发货时累加到 user_quota.paid_quota */
    private Integer quotaDelta;

    private Integer quantity;

    /** 道具单价（分），必须与后台道具价格一致 */
    private Integer priceFen;

    /** 微信回调里的实际支付金额（分），未支付时为 null */
    private Integer actualFen;

    /** 0 现网 / 1 沙箱 */
    private Integer env;

    /** INIT / PAID / DELIVERED / CLOSED */
    private String status;

    /** 微信侧平台单号 */
    private String wxOrderId;

    /** 微信支付交易号 */
    private String transactionId;

    /** 透传数据：openid|goodsId|quota */
    private String attach;

    /** 收到发货推送的次数（排查重试问题用） */
    private Integer notifyCount;

    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    private LocalDateTime deliveredAt;
}
