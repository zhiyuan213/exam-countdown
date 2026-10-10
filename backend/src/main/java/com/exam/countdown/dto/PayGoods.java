package com.exam.countdown.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 额度包（虚拟支付道具）。
 *
 * goodsId 必须与 mp 后台「虚拟支付 → 道具管理」里登记的**道具 ID 完全一致**，
 * priceFen 必须与后台登记的价格完全一致 —— 任一不一致，拉起支付会被微信侧拒绝。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayGoods {

    /** 道具 ID（= 后台道具 ID），前端用它下单 */
    private String goodsId;

    /** 展示名 */
    private String name;

    /** 单价（分） */
    private Integer priceFen;

    /** 本商品发放的额度数 */
    private Integer quota;
}
