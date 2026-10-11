package com.exam.countdown.controller;

import com.exam.countdown.dto.QuotaResp;
import com.exam.countdown.service.QuotaService;
import com.exam.countdown.support.OpenidSupport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 查询当前用户的提醒额度。
 * 免费额度默认 2，limit = freeQuota + paidQuota，按未过期提醒数实时计算占用。
 */
@RestController
@RequestMapping("/api/quota")
public class QuotaController {

    private final QuotaService quotaService;

    public QuotaController(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    @GetMapping
    public Map<String, Object> get(@RequestHeader(value = "X-WX-FROM-OPENID", required = false) String fromOpenid,
                                   @RequestHeader(value = "X-WX-OPENID", required = false) String cloudOpenid) {
        Map<String, Object> out = new LinkedHashMap<>();
        String openid = OpenidSupport.resolve(fromOpenid, cloudOpenid, null);
        if (openid == null) {
            out.put("ok", false);
            out.put("msg", "openid 缺失，请重新进入小程序");
            return out;
        }
        QuotaResp quota = quotaService.view(openid);
        out.put("ok", true);
        out.put("freeQuota", quota.getFreeQuota());
        out.put("paidQuota", quota.getPaidQuota());
        out.put("limit", quota.getLimit());
        out.put("used", quota.getUsed());
        out.put("remaining", quota.getRemaining());
        return out;
    }
}
