package com.exam.countdown.job;

import com.exam.countdown.service.VirtualPayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 虚拟支付发货兜底任务。
 *
 * 为什么需要：微信的「道具发货推送」最多重推 15 次（节奏 15s/15s/30s/3m/10m/20m/30m…），
 * 极端情况下（容器重启、平台抖动、推送被丢弃）可能全部丢失 —— 用户付了钱却没拿到额度。
 * 本任务按固定周期扫一遍「仍未发货」的订单，主动调微信查单接口确认支付状态，确认已付就补发。
 *
 * 触发方式二选一：
 *  - 应用内 @Scheduled（cron 由 wx.pay.recheck-cron 控制，默认每 10 分钟）
 *  - 云托管定时任务 POST /api/pay/recheck?token=***（同一份逻辑，便于手动/外部触发）
 *
 * ⚠️ 前置条件：云托管「最小实例数」必须 ≥ 1，否则实例缩容到 0 时定时任务不会执行（与提醒推送同理）。
 */
@Component
public class PayRecheckJob {

    private static final Logger log = LoggerFactory.getLogger(PayRecheckJob.class);

    private final VirtualPayService payService;

    public PayRecheckJob(VirtualPayService payService) {
        this.payService = payService;
    }

    @Scheduled(cron = "${wx.pay.recheck-cron:0 */10 * * * ?}", zone = "Asia/Shanghai")
    public void run() {
        if (!payService.isEnabled()) {
            return;   // 支付未开通时静默跳过，不刷日志
        }
        try {
            Map<String, Object> r = payService.recheckPending();
            String checked = String.valueOf(r.get("checked"));
            if (!"null".equals(checked) && !"0".equals(checked)) {
                log.info("支付发货兜底查单: checked={} delivered={}", checked, r.get("delivered"));
            }
        } catch (Exception e) {
            // 兜底任务绝不能把异常抛出去影响主流程
            log.warn("支付发货兜底查单异常: {}", e.toString());
        }
    }
}
