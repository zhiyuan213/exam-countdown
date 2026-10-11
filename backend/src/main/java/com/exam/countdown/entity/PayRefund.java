package com.exam.countdown.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 虚拟支付退款记录（二期）。
 *
 * 来源：微信「退款通知」xpay_refund_notify（Android/鸿蒙/Windows 商家在虚拟支付后台
 * 发起退款，或 iOS 用户向 App Store 申请、Apple 审批通过后由微信推送）。
 *
 * 审计留痕用：退款单号 / 金额 / 时间 / 回收额度。pay_order.status=REFUNDED 是「已退款」的
 * 权威状态，本表是明细流水；wx_refund_id 唯一，用于幂等去重。
 */
@Data
@TableName("pay_refund")
public class PayRefund {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务订单号（对应 pay_order.out_trade_no） */
    private String outTradeNo;

    private String openid;

    /** 微信退款单号（WxRefundId） */
    private String wxRefundId;

    /** 商户退款单号（MchRefundId） */
    private String mchRefundId;

    /** 退款单对应的微信订单号（WxOrderId） */
    private String wxOrderId;

    /** 退款金额（分） */
    private Integer refundFee;

    /** 退款状态：0 成功，非 0 失败 */
    private Integer retCode;

    private String retMsg;

    /** 本次实际回收的额度数（订单未发货时为 0） */
    private Integer quotaReduced;

    private LocalDateTime succAt;

    private LocalDateTime createdAt;
}
