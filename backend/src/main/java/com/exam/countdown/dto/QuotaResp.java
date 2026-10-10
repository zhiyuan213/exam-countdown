package com.exam.countdown.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 额度视图。limit = freeQuota + paidQuota；
 * used = 当前未过期的自建提醒数；remaining = limit - used。
 */
@Data
@AllArgsConstructor
public class QuotaResp {
    private int freeQuota;
    private int paidQuota;
    private int limit;
    private int used;
    private int remaining;
}
