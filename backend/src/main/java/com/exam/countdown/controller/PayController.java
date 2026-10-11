package com.exam.countdown.controller;

import com.exam.countdown.dto.PayGoods;
import com.exam.countdown.service.VirtualPayService;
import com.exam.countdown.support.OpenidSupport;
import com.exam.countdown.support.PushSignUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 虚拟支付（二期）接口。
 *
 *   GET  /api/pay/goods          额度包列表
 *   POST /api/pay/order          下单，返回 signData / paySig / signature
 *   GET  /api/pay/order/{no}     查本地订单状态（支付后确认）
 *   GET  /api/pay/notify         微信「消息推送」URL 有效性校验（原样回 echostr）
 *   POST /api/pay/notify         推送入口，按 Event 分派：
 *                                  xpay_goods_deliver_notify          道具发货（幂等发货）
 *                                  xpay_refund_notify                  退款（置 REFUNDED + 回收额度）
 *                                  xpay_subscribe_ios_refund_query_notify iOS 退款问询（3 秒内应答）
 *   POST /api/pay/recheck        兜底查单（需 token）：补发未发货订单 + 回收已退款订单
 *
 * 推送接收方式（二选一，推荐前者）：
 *   ① 微信云托管控制台 → 设置 → 全局设置 → 消息推送：绑定环境/服务，path=/api/pay/notify（内网直达，免签名）
 *   ② mp 后台消息推送 URL：https://<正式域名>/api/pay/notify（云托管默认域名会被拒绝，需签名校验）
 */
@RestController
@RequestMapping("/api/pay")
public class PayController {

    private static final Logger log = LoggerFactory.getLogger(PayController.class);

    private final ObjectMapper om = new ObjectMapper();
    private final VirtualPayService payService;

    @Value("${wx.pay.push-token:}")
    private String pushToken;

    @Value("${remind.job-token:change-me}")
    private String jobToken;

    public PayController(VirtualPayService payService) {
        this.payService = payService;
    }

    // ==========================================================
    // 商品 / 下单 / 查单
    // ==========================================================

    @GetMapping("/goods")
    public Map<String, Object> goods() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<PayGoods> list = payService.goods();
        out.put("ok", true);
        out.put("enabled", payService.isEnabled() && !list.isEmpty());
        out.put("goods", list);
        return out;
    }

    @PostMapping("/order")
    public Map<String, Object> createOrder(
            @RequestHeader(value = "X-WX-FROM-OPENID", required = false) String fromOpenid,
            @RequestHeader(value = "X-WX-OPENID", required = false) String cloudOpenid,
            @RequestBody(required = false) Map<String, Object> body) {
        String openid = OpenidSupport.resolve(fromOpenid, cloudOpenid,
                body == null ? null : str(body.get("openid")));
        String goodsId = body == null ? null : str(body.get("goodsId"));
        String code = body == null ? null : str(body.get("code"));
        Map<String, Object> res = payService.createOrder(openid, goodsId, code);
        // 下单是支付链路的起点：失败时这行能直接看出是 openid 缺失、商品不存在还是签名问题
        log.info("下单结果 openid={} goodsId={} hasCode={} ok={} msg={}",
                openid, goodsId, code != null && !code.isBlank(),
                res.get("ok"), res.get("msg"));
        return res;
    }

    @GetMapping("/order/{outTradeNo}")
    public Map<String, Object> order(@PathVariable String outTradeNo,
                                     @RequestHeader(value = "X-WX-FROM-OPENID", required = false) String fromOpenid,
                                     @RequestHeader(value = "X-WX-OPENID", required = false) String cloudOpenid) {
        return payService.localOrder(outTradeNo, OpenidSupport.resolve(fromOpenid, cloudOpenid, null));
    }

    /** 兜底查单：挂在云托管定时任务上（和提醒任务一样带 token） */
    @PostMapping("/recheck")
    public Map<String, Object> recheck(@RequestParam(required = false) String token) {
        if (!jobToken.equals(token)) {
            return Map.of("ok", false, "msg", "token 无效");
        }
        return payService.recheckPending();
    }

    /**
     * 诊断查单：原样返回「发给微信的请求体」和「微信的应答」，用于核对云调用通道
     * 是否转发 POST 请求体、以及错误码的真实含义。需 jobToken。
     */
    @GetMapping("/query")
    public Map<String, Object> queryOne(@RequestParam(required = false) String no,
                                        @RequestParam(required = false) String token) {
        if (!jobToken.equals(token)) {
            return Map.of("ok", false, "msg", "token 无效");
        }
        if (no == null || no.isBlank()) {
            return Map.of("ok", false, "msg", "缺少订单号参数 no");
        }
        return payService.queryOrderDebug(no);
    }

    /** 管理端：订单 + 用户额度总览（对账/修数用），需 jobToken */
    @GetMapping("/admin/overview")
    public Map<String, Object> adminOverview(@RequestParam(required = false) String token) {
        if (!jobToken.equals(token)) {
            return Map.of("ok", false, "msg", "token 无效");
        }
        return payService.adminOverview();
    }

    /** 管理端：直接设置某用户 paid_quota（修数用），需 jobToken */
    @PostMapping("/admin/paid-quota")
    public Map<String, Object> adminSetPaidQuota(@RequestParam(required = false) String token,
                                                 @RequestParam(required = false) String openid,
                                                 @RequestParam(required = false) Integer quota) {
        if (!jobToken.equals(token)) {
            return Map.of("ok", false, "msg", "token 无效");
        }
        if (openid == null || openid.isBlank() || quota == null || quota < 0) {
            return Map.of("ok", false, "msg", "参数无效：需要 openid 和 quota(>=0)");
        }
        return payService.setPaidQuota(openid, quota);
    }

    // ==========================================================
    // 发货推送
    // ==========================================================

    /** URL 校验：微信配置推送地址时会 GET 一次，需原样返回 echostr */
    @GetMapping(value = "/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String verify(@RequestParam(required = false) String signature,
                         @RequestParam(required = false) String timestamp,
                         @RequestParam(required = false) String nonce,
                         @RequestParam(required = false) String echostr) {
        if (pushToken != null && !pushToken.isBlank() && !PushSignUtil.check(pushToken, timestamp, nonce, signature)) {
            log.warn("推送 URL 校验签名不通过（timestamp={} nonce={}）", timestamp, nonce);
            return "signature check failed";
        }
        log.info("推送 URL 校验通过");
        return echostr == null ? "ok" : echostr;
    }

    /**
     * 道具发货推送。微信按配置的格式发 JSON 或 XML，两种都要能解析并按要求应答：
     *   JSON -> {"ErrCode":0,"ErrMsg":"success"}；XML -> <xml><Errcode>0</Errcode>...</xml>
     * 要求 HTTP 200；应答不合法微信最多重推 15 次。
     */
    @PostMapping(value = "/notify", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
    public ResponseEntity<String> notify(@RequestBody(required = false) String raw,
                                         @RequestHeader(value = "x-wx-sources", required = false) String wxSources,
                                         @RequestParam(required = false) String signature,
                                         @RequestParam(required = false) String timestamp,
                                         @RequestParam(required = false) String nonce) {
        boolean xml = raw != null && raw.trim().startsWith("<");
        log.info("收到支付消息推送: {} (x-wx-sources={})", raw, wxSources);

        // 云托管内网通道以 x-wx-sources 头标识微信侧来源。
        // 实测：云托管转发的微信推送既不带 signature 参数、也不带 x-wx-sources 头，
        // 因此「无签名参数」不能判为失败，否则所有推送都会被拒（曾因此漏发货）。
        // 处理：有签名参数但校验失败 -> 直接拒；无签名参数 -> 放行到 deliver()，
        //      由 deliver() 调微信查单核验该单确实已支付后才发货（防伪造推送白拿额度）。
        boolean viaWxInternal = wxSources != null && !wxSources.isBlank();
        boolean sigPresent = signature != null && !signature.isBlank();
        boolean tokenConfigured = pushToken != null && !pushToken.isBlank();
        boolean sigVerified = viaWxInternal
                || (tokenConfigured && sigPresent && PushSignUtil.check(pushToken, timestamp, nonce, signature));
        if (tokenConfigured && sigPresent && !sigVerified) {
            log.warn("推送签名校验不通过，拒绝处理");
            return reply(xml, false, "signature check failed");
        }

        JsonNode node;
        try {
            node = xml ? PushSignUtil.xmlToJson(om, raw) : om.readTree(raw);
        } catch (Exception e) {
            log.error("推送报文解析失败: {}", e.toString());
            return reply(xml, false, "parse failed");
        }

        String event = node.path("Event").asText("");

        // ① 道具发货推送
        if ("xpay_goods_deliver_notify".equals(event)) {
            boolean ok;
            try {
                ok = payService.deliver(node, sigVerified);
            } catch (Exception e) {
                log.error("发货处理异常: {}", e.toString());
                ok = false;
            }
            return reply(xml, ok, ok ? "success" : "deliver failed");
        }

        // ② 退款通知：更新订单状态 + 回收额度（资损相关，必须处理）
        if ("xpay_refund_notify".equals(event)) {
            boolean ok;
            try {
                ok = payService.refund(node);
            } catch (Exception e) {
                log.error("退款处理异常: {}", e.toString());
                ok = false;
            }
            return reply(xml, ok, ok ? "success" : "refund failed");
        }

        // ③ iOS 退款问询：需在 3 秒内应答，且应答体额外带 IosRefundQueryResponse
        if ("xpay_subscribe_ios_refund_query_notify".equals(event)) {
            Map<String, Object> decision;
            try {
                decision = payService.decideIosRefundQuery(node);
            } catch (Exception e) {
                log.error("iOS 退款问询处理异常: {}", e.toString());
                decision = Map.of("result_code", 1, "result_info", "拒绝退款",
                        "evidence", "系统繁忙，暂时无法受理退款申请，请稍后重试。");
            }
            return iosRefundReply(decision);
        }

        log.info("非发货/退款事件（Event={}），直接确认", event);
        return reply(xml, true, "success");
    }

    /**
     * iOS 退款问询的专用应答：标准应答体外再带 IosRefundQueryResponse（官方要求）。
     * 推送模式配置为 JSON，故固定用 JSON 应答。
     */
    private ResponseEntity<String> iosRefundReply(Map<String, Object> decision) {
        String decisionJson;
        try {
            decisionJson = om.writeValueAsString(decision);
        } catch (Exception e) {
            decisionJson = "{\"result_code\":1,\"result_info\":\"拒绝退款\",\"evidence\":\"系统异常\"}";
        }
        String body = "{\"ErrCode\":0,\"ErrMsg\":\"success\",\"IosRefundQueryResponse\":" + decisionJson + "}";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static ResponseEntity<String> reply(boolean xml, boolean ok, String msg) {
        String body;
        MediaType type;
        if (xml) {
            type = MediaType.APPLICATION_XML;
            body = ok
                    ? "<xml><Errcode>0</Errcode><ErrMsg><![CDATA[success]]></ErrMsg></xml>"
                    : "<xml><Errcode>1</Errcode><ErrMsg><![CDATA[" + msg + "]]></ErrMsg></xml>";
        } else {
            type = MediaType.APPLICATION_JSON;
            body = "{\"ErrCode\":" + (ok ? 0 : 1) + ",\"ErrMsg\":\"" + msg + "\"}";
        }
        return ResponseEntity.ok().contentType(type).body(body);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
