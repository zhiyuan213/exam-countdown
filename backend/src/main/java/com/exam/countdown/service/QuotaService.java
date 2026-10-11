package com.exam.countdown.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.exam.countdown.dto.QuotaResp;
import com.exam.countdown.entity.UserExam;
import com.exam.countdown.entity.UserQuota;
import com.exam.countdown.mapper.UserExamMapper;
import com.exam.countdown.mapper.UserQuotaMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 用户提醒额度。
 *
 * 规则（刻意做成「按当前有效提醒数实时计算」，而不是扣减累计）：
 *   limit = freeQuota + paidQuota，表示可同时管理的提醒数；
 *   used  = 当前 status=1 且未过期的自建提醒数；
 *   删除提醒或提醒过期都会自动释放额度 —— 不消耗用户已购额度，避免退款纠纷。
 */
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    /** 免费额度：每个用户可同时管理 2 个设置提醒 */
    public static final int DEFAULT_FREE_QUOTA = 2;

    private final UserQuotaMapper quotaMapper;
    private final UserExamMapper userExamMapper;

    public QuotaService(UserQuotaMapper quotaMapper, UserExamMapper userExamMapper) {
        this.quotaMapper = quotaMapper;
        this.userExamMapper = userExamMapper;
    }

    /** 取额度记录，没有就按默认值建一条 */
    public UserQuota ensure(String openid) {
        UserQuota q = quotaMapper.selectById(openid);
        if (q == null) {
            q = new UserQuota();
            q.setOpenid(openid);
            q.setFreeQuota(DEFAULT_FREE_QUOTA);
            q.setPaidQuota(0);
            try {
                quotaMapper.insert(q);
            } catch (Exception e) {
                // 并发首次创建：唯一键冲突时回读一次
                log.warn("user_quota 首次创建冲突，回读: {}", e.toString());
                q = quotaMapper.selectById(openid);
                if (q == null) {
                    throw e;
                }
            }
        }
        if (q.getFreeQuota() == null) {
            q.setFreeQuota(DEFAULT_FREE_QUOTA);
        }
        if (q.getPaidQuota() == null) {
            q.setPaidQuota(0);
        }
        return q;
    }

    /** 当前占用（未过期的有效提醒数） */
    public int used(String openid) {
        Long c = userExamMapper.selectCount(new QueryWrapper<UserExam>()
                .eq("openid", openid)
                .eq("status", 1)
                .ge("exam_date", LocalDate.now()));
        return c == null ? 0 : c.intValue();
    }

    public QuotaResp view(String openid) {
        UserQuota q = ensure(openid);
        int limit = q.getFreeQuota() + q.getPaidQuota();
        int used = used(openid);
        return new QuotaResp(q.getFreeQuota(), q.getPaidQuota(), limit, used, Math.max(limit - used, 0));
    }

    public int remaining(String openid) {
        return view(openid).getRemaining();
    }

    /**
     * 支付成功后累加已购额度。
     *
     * 用 SQL 原子自增（`paid_quota = paid_quota + delta`）而不是「读出来加再写回」——
     * 后者在并发发货（微信推送 / 兜底定时任务同时到达，或用户连买两单）时会丢更新：
     * 两个请求读到同一基数、各自写回，导致少发额度。
     */
    public QuotaResp addPaidQuota(String openid, int delta) {
        if (delta == 0) {
            return view(openid);
        }
        ensure(openid);   // 保证记录存在（首次购买的用户可能还没有 user_quota 行）
        quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("openid", openid)
                .setSql("paid_quota = IFNULL(paid_quota, 0) + (" + delta + ")"));
        log.info("paid_quota 原子累加 openid={} delta={}", openid, delta);
        return view(openid);
    }

    /**
     * 退款后回收已购额度（二期退款回调调用）。
     *
     * 与 {@link #addPaidQuota} 对称，同样用 SQL 原子自减，并且用 GREATEST(..., 0) 兜底：
     * 用户可能已经把这批额度用掉了一部分（额度是「可同时管理的提醒数」，用完的提醒不会消失），
     * 回收时只保证 paid_quota 不为负 —— 已有的提醒不受影响，用户只是不能再新增。
     */
    public QuotaResp reducePaidQuota(String openid, int delta) {
        if (delta <= 0) {
            return view(openid);
        }
        ensure(openid);
        quotaMapper.update(null, new UpdateWrapper<UserQuota>()
                .eq("openid", openid)
                .setSql("paid_quota = GREATEST(IFNULL(paid_quota, 0) - (" + delta + "), 0)"));
        log.info("paid_quota 原子扣减 openid={} delta={}", openid, delta);
        return view(openid);
    }
}
