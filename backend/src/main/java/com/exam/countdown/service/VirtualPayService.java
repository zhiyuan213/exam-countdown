package com.exam.countdown.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.exam.countdown.dto.PayGoods;
import com.exam.countdown.entity.PayOrder;
import com.exam.countdown.entity.PayRefund;
import com.exam.countdown.mapper.PayOrderMapper;
import com.exam.countdown.mapper.PayRefundMapper;
import com.exam.countdown.support.PaySignUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 微信小程序虚拟支付（二期）：额度包购买。
 *
 * 模式：道具直购 short_series_goods（低频单次购买，正好适合「买额度包」）。
 * 主链路：前端下单 → 本服务组装 signData 并双签名 → wx.requestVirtualPayment 拉起支付
 *        → 微信「道具发货推送」xpay_goods_deliver_notify → 本服务幂等发货（额度 +N）。
 * 兜底：定时/手动调用 {@link #recheckPending()} 走 /xpay/query_order 查单补发（推送最多重试 15 次，
 *      极端情况下可能丢，查单是官方建议的第二道保险）。
 *
 * 未开通 / 未配置 OfferID 时 enabled=false，接口会明确返回「支付未开通」，不会污染线上行为。
 */
@Service
public class VirtualPayService {

    private static final Logger log = LoggerFactory.getLogger(VirtualPayService.class);

    /** 道具直购模式 */
    public static final String MODE_GOODS = "short_series_goods";

    /** 拉起支付时 paySig 的 uri 固定值 */
    private static final String URI_REQUEST_PAYMENT = "requestVirtualPayment";

    /** 查单接口 uri（参与 paySig 计算，不带 query string） */
    private static final String URI_QUERY_ORDER = "/xpay/query_order";

    /** 查单核验不可用时的退避时长：容器内直连 api.weixin.qq.com 常因证书链校验失败而不可用 */
    private static final long QUERY_BACKOFF_MS = 5 * 60_000L;

    /**
     * 查单接口「数据不存在」错误码。实测：已付款订单返回 errcode=0 + order.status，
     * 从未付款（微信侧无订单记录）的订单返回 268490002「数据不存在」。
     * 因此该码可安全判定为「明确未支付」，用于拒绝伪造推送。
     */
    private static final int QUERY_ERR_ORDER_NOT_FOUND = 268490002;

    /**
     * 查单 order.status 中「已退款」的两个取值（官方枚举）：
     *   5 = 订单已经退款（商家发起）
     *   8 = 用户退款完成（用户向 App Store 申请后 Apple 批准）
     * 其余 6 已关闭 / 7 退款失败 / 9 回收广告金 / 10 分账回退 都不算「已退款」。
     */
    private static final int ORDER_STATUS_REFUNDED = 5;
    private static final int ORDER_STATUS_USER_REFUNDED = 8;

    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper om = new ObjectMapper();

    private final PayOrderMapper payOrderMapper;
    private final PayRefundMapper payRefundMapper;
    private final QuotaService quotaService;
    private final TokenManager tokenManager;

    @Value("${wx.appid:}")
    private String appid;

    @Value("${wx.secret:}")
    private String secret;

    @Value("${wx.cloud-call:true}")
    private boolean cloudCallFirst;

    /** 总开关：未开通虚拟支付时保持 false */
    @Value("${wx.pay.enabled:false}")
    private boolean enabled;

    /** 虚拟支付 OfferID（支付应用 ID） */
    @Value("${wx.pay.offer-id:}")
    private String offerId;

    /** 现网 AppKey */
    @Value("${wx.pay.appkey:}")
    private String appKeyProd;

    /** 沙箱 AppKey */
    @Value("${wx.pay.appkey-sandbox:}")
    private String appKeySandbox;

    /** 0 现网 / 1 沙箱 */
    @Value("${wx.pay.env:0}")
    private int env;

    /** 额度包配置：goodsId:priceFen:quota:name，多项用 ; 分隔 */
    @Value("${wx.pay.goods:}")
    private String goodsRaw;

    private volatile List<PayGoods> goodsCache;

    /** 查单核验的退避截止时间戳（ms）：查单不可用时短期内不再重试，避免每条推送空等超时 */
    private volatile long queryBackoffUntil = 0L;

    public VirtualPayService(PayOrderMapper payOrderMapper,
                             PayRefundMapper payRefundMapper,
                             QuotaService quotaService,
                             TokenManager tokenManager) {
        this.payOrderMapper = payOrderMapper;
        this.payRefundMapper = payRefundMapper;
        this.quotaService = quotaService;
        this.tokenManager = tokenManager;
    }

    // ==========================================================
    // 商品
    // ==========================================================

    /** 额度包列表。参数格式：goodsId:priceFen:quota:name（name 可含冒号，故 limit=4） */
    public List<PayGoods> goods() {
        if (goodsCache == null) {
            List<PayGoods> list = new ArrayList<>();
            for (String seg : goodsRaw.split(";")) {
                String s = seg.trim();
                if (s.isEmpty()) {
                    continue;
                }
                String[] p = s.split(":", 4);
                if (p.length < 2) {
                    log.warn("额度包配置格式不合法，已跳过: {}", s);
                    continue;
                }
                try {
                    String id = p[0].trim();
                    int price = Integer.parseInt(p[1].trim());
                    int quota = p.length > 2 ? Integer.parseInt(p[2].trim()) : 1;
                    String name = p.length > 3 ? p[3].trim() : (id + " 额度包");
                    list.add(new PayGoods(id, name, price, quota));
                } catch (NumberFormatException e) {
                    log.warn("额度包配置数字解析失败，已跳过: {}", s);
                }
            }
            goodsCache = Collections.unmodifiableList(list);
        }
        return goodsCache;
    }

    private PayGoods findGoods(String goodsId) {
        if (goodsId == null || goodsId.isBlank()) {
            return null;
        }
        for (PayGoods g : goods()) {
            if (g.getGoodsId().equals(goodsId.trim())) {
                return g;
            }
        }
        return null;
    }

    /** 环境对应的 AppKey（env=1 用沙箱，否则用现网） */
    private String currentAppKey() {
        return env == 1 ? appKeySandbox : appKeyProd;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ==========================================================
    // 下单
    // ==========================================================

    /**
     * 创建订单并生成前端拉起支付所需的三件套（signData / paySig / signature）。
     *
     * @param openid  可信 openid（来自云托管注入的请求头，可为 null 时用 code 换回的 openid）
     * @param goodsId 道具 ID
     * @param code    wx.login() 拿到的一次性 code，用于换 session_key（用户态签名必需）
     */
    public Map<String, Object> createOrder(String openid, String goodsId, String code) {
        if (!enabled) {
            return fail("PAY_DISABLED", "支付功能尚未开通");
        }
        if (offerId == null || offerId.isBlank()) {
            return fail("PAY_NOT_CONFIGURED", "支付参数未配置（OfferID）");
        }
        String appKey = currentAppKey();
        if (appKey == null || appKey.isBlank()) {
            return fail("PAY_NOT_CONFIGURED", "支付参数未配置（AppKey）");
        }
        PayGoods goods = findGoods(goodsId);
        if (goods == null) {
            return fail("GOODS_NOT_FOUND", "额度包不存在，请刷新后重试");
        }
        if (code == null || code.isBlank()) {
            return fail("CODE_REQUIRED", "缺少登录凭证，请重新进入小程序后重试");
        }

        Session session;
        try {
            session = code2session(code);
        } catch (Exception e) {
            log.error("支付下单 code2session 失败: {}", e.toString());
            return fail("SESSION_FAILED", "获取登录态失败，请重新进入小程序后重试");
        }

        String payOpenid = (openid != null && !openid.isBlank()) ? openid.trim() : session.openid;
        if (session.openid != null && !session.openid.equals(payOpenid)) {
            // 正常不该出现；出现说明请求头身份与 code 身份不一致，先记录下来
            log.warn("下单 openid 与 code 身份不一致: header={} code={}", payOpenid, session.openid);
        }

        String outTradeNo = genOrderNo();
        String attach = payOpenid + "|" + goods.getGoodsId() + "|" + goods.getQuota();

        // ⚠️ 字段顺序即签名内容，顺序与内容都不可改（Jackson 对 LinkedHashMap 保序）
        LinkedHashMap<String, Object> signData = new LinkedHashMap<>();
        signData.put("offerId", offerId);
        signData.put("buyQuantity", 1);
        signData.put("env", env);
        signData.put("currencyType", "CNY");
        signData.put("productId", goods.getGoodsId());
        signData.put("goodsPrice", goods.getPriceFen());
        signData.put("outTradeNo", outTradeNo);
        signData.put("attach", attach);

        String signDataJson;
        try {
            signDataJson = om.writeValueAsString(signData);
        } catch (Exception e) {
            log.error("signData 序列化失败: {}", e.toString());
            return fail("SIGN_FAILED", "下单失败，请稍后重试");
        }

        String paySig = PaySignUtil.hmacSha256Hex(appKey, URI_REQUEST_PAYMENT + "&" + signDataJson);
        String signature = PaySignUtil.hmacSha256Hex(session.sessionKey, signDataJson);

        PayOrder order = new PayOrder();
        order.setOutTradeNo(outTradeNo);
        order.setOpenid(payOpenid);
        order.setProductId(goods.getGoodsId());
        order.setGoodsName(goods.getName());
        order.setQuotaDelta(goods.getQuota());
        order.setQuantity(1);
        order.setPriceFen(goods.getPriceFen());
        order.setEnv(env);
        order.setStatus("INIT");
        order.setAttach(attach);
        order.setNotifyCount(0);
        order.setCreatedAt(LocalDateTime.now());
        payOrderMapper.insert(order);
        log.info("虚拟支付下单 openid={} goods={} order={} priceFen={}", payOpenid, goods.getGoodsId(), outTradeNo, goods.getPriceFen());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("orderId", outTradeNo);
        out.put("mode", MODE_GOODS);
        out.put("env", env);
        out.put("signData", signDataJson);
        out.put("paySig", paySig);
        out.put("signature", signature);
        out.put("goods", goods);
        return out;
    }

    /** 查询本地订单状态（前端支付成功回调后确认用） */
    public Map<String, Object> localOrder(String outTradeNo, String openid) {
        PayOrder order = payOrderMapper.selectById(outTradeNo);
        Map<String, Object> out = new LinkedHashMap<>();
        if (order == null) {
            out.put("ok", false);
            out.put("code", "ORDER_NOT_FOUND");
            out.put("msg", "订单不存在");
            return out;
        }
        if (openid != null && !openid.isBlank() && !openid.trim().equals(order.getOpenid())) {
            // openid 不匹配（云托管未注入请求头时允许放行，只记录）
            log.warn("查询订单 openid 不一致: req={} order={}", openid, order.getOpenid());
        }
        out.put("ok", true);
        out.put("orderId", order.getOutTradeNo());
        out.put("status", order.getStatus());
        out.put("quotaDelta", order.getQuotaDelta());
        out.put("delivered", "DELIVERED".equals(order.getStatus()));
        return out;
    }

    // ==========================================================
    // 发货（微信道具发货推送）
    // ==========================================================

    /**
     * 处理 xpay_goods_deliver_notify。
     * 幂等两道闸：① 订单已 DELIVERED 直接返回成功；② 同一微信平台单号已发过货也返回成功。
     *
     * @param trusted 推送是否已通过签名 / 内网通道验证。为 false 时（实测云托管转发的微信推送
     *                不带签名参数）先调 /xpay/query_order 核验该单是否真的已支付：明确查到「未支付」
     *                则拒绝发货，防止伪造推送白拿额度；查单不可用（接口异常 / 字段口径不符）时
     *                不阻断，避免因查单故障漏发货（漏发货可由 recheck 定时兜底补发）。
     * @return true = 已确认发货（HTTP 应答 0，微信不再重推）
     */
    public boolean deliver(JsonNode notify, boolean trusted) {
        String outTradeNo = text(notify, "OutTradeNo");
        String notifyOpenid = text(notify, "OpenId");
        Integer notifyEnv = intVal(notify, "Env");
        JsonNode goodsInfo = notify.path("GoodsInfo");
        JsonNode payInfo = notify.path("WeChatPayInfo");
        String wxOrderId = text(payInfo, "MchOrderNo");
        String transactionId = text(payInfo, "TransactionId");
        Integer actualFen = intVal(goodsInfo, "ActualPrice");
        Integer quantity = intVal(goodsInfo, "Quantity");

        log.info("收到道具发货推送: outTradeNo={} openid={} env={} wxOrderId={} productId={} qty={} actualFen={}",
                outTradeNo, notifyOpenid, notifyEnv, wxOrderId, text(goodsInfo, "ProductId"), quantity, actualFen);

        if (outTradeNo == null || outTradeNo.isBlank()) {
            log.error("发货推送缺少 OutTradeNo，无法处理: {}", notify);
            return false;
        }
        if (notifyEnv != null && notifyEnv != env) {
            log.warn("发货推送 env={} 与当前配置 env={} 不一致（若为沙箱推送请调 XPAY_ENV）", notifyEnv, env);
        }

        PayOrder order = payOrderMapper.selectById(outTradeNo);
        if (order == null) {
            // 返回失败让微信重试，给我们排查（订单可能落在别的环境/库里）
            log.error("发货推送找不到订单 outTradeNo={}，将让微信重试", outTradeNo);
            return false;
        }
        if ("DELIVERED".equals(order.getStatus())) {
            log.info("订单已发货，幂等跳过: {}", outTradeNo);
            return true;
        }
        if ("REFUNDED".equals(order.getStatus())) {
            // 已退款的订单若还收到（或被人伪造）发货推送，绝不能发货，否则用户退完款又白拿额度
            log.warn("订单已退款，拒绝发货: {}", outTradeNo);
            return true;
        }
        if (wxOrderId != null && !wxOrderId.isBlank()) {
            Long dup = payOrderMapper.selectCount(new QueryWrapper<PayOrder>()
                    .eq("wx_order_id", wxOrderId)
                    .eq("status", "DELIVERED"));
            if (dup != null && dup > 0) {
                log.warn("同一微信平台单号已发过货，跳过: wxOrderId={} outTradeNo={}", wxOrderId, outTradeNo);
                return true;
            }
        }

        // 未签名推送（云托管转发的微信推送常态）：先查单核验是否真的已支付
        if (!trusted) {
            Boolean paid = queryPaid(outTradeNo, notifyOpenid == null ? order.getOpenid() : notifyOpenid,
                    notifyEnv == null ? env : notifyEnv);
            if (Boolean.FALSE.equals(paid)) {
                log.error("未签名推送且查单确认未支付，拒绝发货: {}", outTradeNo);
                return false;
            }
            if (paid == null) {
                log.warn("未签名推送，查单无法确认（放行发货，仅依赖本地订单校验）: {}", outTradeNo);
            } else {
                log.info("未签名推送，已查单确认支付: {}", outTradeNo);
            }
        }

        // 原子占用订单并发放额度（CAS）：微信推送与兜底定时任务可能并发处理同一订单，
        // 只有把 status 从非 DELIVERED 改成 DELIVERED 成功的那一方才发额度，避免重复发货
        int delta = order.getQuotaDelta() == null ? 0 : order.getQuotaDelta();
        try {
            claimAndDeliver(outTradeNo, order.getOpenid(), delta,
                    wxOrderId, transactionId, actualFen, "push");
        } catch (Exception e) {
            // 额度发放失败要抛出去：订单已是 DELIVERED，靠人工/日志补偿
            log.error("额度发放失败 order={} openid={} delta={}: {}", outTradeNo, order.getOpenid(), delta, e.toString());
            throw e;
        }
        return true;
    }

    /**
     * 原子占用订单并发货：用 CAS（`UPDATE ... WHERE status <> 'DELIVERED'`）把订单置为已发货，
     * **只有拿到 1 行更新影响的调用方才会发放额度**。
     *
     * 为什么需要：发货有两条触发通道 —— ① 微信发货推送（实时）② {@link #recheckPending()} 兜底查单
     * （每 10 分钟定时）。两者可能同时读到同一笔 INIT 订单、同时通过状态判断，若不做 CAS 就会重复发放。
     *
     * @return true = 本次调用真正完成了发货；false = 已被其他通道抢先发货
     */
    private boolean claimAndDeliver(String outTradeNo, String openid, int delta,
                                    String wxOrderId, String transactionId, Integer actualFen,
                                    String source) {
        UpdateWrapper<PayOrder> uw = new UpdateWrapper<PayOrder>()
                .eq("out_trade_no", outTradeNo)
                // 只允许从「未发货」状态发货：REFUNDED 的订单绝不能再发货（否则用户退完款又白拿额度）
                .in("status", "INIT", "PAID")
                .set("status", "DELIVERED")
                .set("delivered_at", LocalDateTime.now())
                .setSql("notify_count = IFNULL(notify_count, 0) + 1")
                .setSql("paid_at = IFNULL(paid_at, NOW())");
        if (wxOrderId != null && !wxOrderId.isBlank()) {
            uw.set("wx_order_id", wxOrderId);
        }
        if (transactionId != null && !transactionId.isBlank()) {
            uw.set("transaction_id", transactionId);
        }
        if (actualFen != null) {
            uw.set("actual_fen", actualFen);
        }
        if (payOrderMapper.update(null, uw) != 1) {
            log.info("订单已被其他通道发货，跳过（并发保护）order={} via={}", outTradeNo, source);
            return false;
        }
        if (delta > 0) {
            quotaService.addPaidQuota(openid, delta);
        }
        log.info("发货完成 order={} openid={} +{} 个额度 via={}", outTradeNo, openid, delta, source);
        return true;
    }

    // ==========================================================
    // 退款（xpay_refund_notify）
    // ==========================================================

    /**
     * 处理微信「退款通知」xpay_refund_notify。
     *
     * 触发场景：
     *   ① Android/鸿蒙/Windows —— 商家在 mp 后台「虚拟支付 → 交易订单」发起退款；
     *   ② iOS —— 开发者无法主动退，用户向 App Store 申请、Apple 最终批准后由微信推送。
     * 官方要求开发者收到后「更新订单状态、回收道具」：微信动不了我们库里的额度，必须自己做。
     *
     * 幂等：订单已 REFUNDED 直接确认；同 wx_refund_id 只落一条流水。
     * 只有退款成功（RetCode=0）才回收额度；失败（RetCode≠0）仅留痕，不动用户额度。
     *
     * @return true = 已确认处理（HTTP 应答 0，微信不再重推）
     */
    public boolean refund(JsonNode notify) {
        // 退款推送里「商户订单号」是 MchOrderId（= 我们的 outTradeNo）；兼容性地再读一次 OutTradeNo
        String outTradeNo = firstNonBlank(text(notify, "MchOrderId"), text(notify, "OutTradeNo"));
        String notifyOpenid = text(notify, "OpenId");
        String wxRefundId = text(notify, "WxRefundId");
        String mchRefundId = text(notify, "MchRefundId");
        String wxOrderId = text(notify, "WxOrderId");
        Integer refundFee = intVal(notify, "RefundFee");
        Integer retCode = intVal(notify, "RetCode");
        String retMsg = text(notify, "RetMsg");
        Long succTs = longVal(notify, "RefundSuccTimestamp");

        log.info("收到退款推送: outTradeNo={} openid={} wxRefundId={} mchRefundId={} refundFee={} retCode={} retMsg={}",
                outTradeNo, notifyOpenid, wxRefundId, mchRefundId, refundFee, retCode, retMsg);

        if (outTradeNo == null || outTradeNo.isBlank()) {
            log.error("退款推送缺少订单号（MchOrderId / OutTradeNo 均为空），将让微信重试: {}", notify);
            return false;
        }

        PayOrder order = payOrderMapper.selectById(outTradeNo);
        if (order == null) {
            log.error("退款推送找不到订单 outTradeNo={}，将让微信重试", outTradeNo);
            return false;
        }

        // 退款失败：不回收额度，仅留痕（用户额度仍在，等商家重试退款）
        // RetCode 归一为非空整数（缺省视为 0 成功），并作为幂等留痕键的一部分
        int rc = retCode == null ? 0 : retCode;
        if (rc != 0) {
            log.warn("退款未成功（RetCode={} {}），仅留痕不回收额度: {}", rc, retMsg, outTradeNo);
            recordRefund(order, wxRefundId, mchRefundId, wxOrderId, refundFee, rc, retMsg, succTs, 0);
            return true;
        }

        // 原子占用退款（CAS）：返回值直接告诉我们「退款那一刻订单是否已发货」，避免先读后写的竞态
        int claim = claimRefund(outTradeNo);
        if (claim == 0) {
            log.info("订单已处于退款状态（或状态不可占用），幂等跳过: {}", outTradeNo);
            recordRefund(order, wxRefundId, mchRefundId, wxOrderId, refundFee, rc, retMsg, succTs, 0);
            return true;
        }

        int reduced = 0;
        if (claim == 1) {
            // 原本已发货 -> 额度已发给用户，回收
            int delta = order.getQuotaDelta() == null ? 0 : order.getQuotaDelta();
            if (delta > 0) {
                quotaService.reducePaidQuota(order.getOpenid(), delta);
                reduced = delta;
            }
        } else {
            // 原本未发货 -> 额度从未发放，无需回收
            log.info("订单退款时尚未发货，无额度需回收: order={}", outTradeNo);
        }
        recordRefund(order, wxRefundId, mchRefundId, wxOrderId, refundFee, rc, retMsg, succTs, reduced);
        log.info("退款处理完成 order={} openid={} 回收额度={} wxRefundId={}",
                outTradeNo, order.getOpenid(), reduced, wxRefundId);
        return true;
    }

    /**
     * 原子占用退款（CAS），返回值即「订单在退款那一刻是否已发货」：
     *   1 = 原本已发货（DELIVERED -> REFUNDED），需要回收额度
     *   2 = 原本未发货（INIT/PAID -> REFUNDED），无需回收额度
     *   0 = 已是 REFUNDED（或其他状态），本次不占用
     *
     * 为什么拆两步：必须先按「当前状态」判定是否需要回收额度。若先 selectById 再判断再 update，
     * 中间可能被发货通道（推送 / 兜底查单）插入把 INIT 改成 DELIVERED，导致漏回收或重复回收。
     * 把条件写进 SQL 的 WHERE 里，由数据库保证判定与写入是原子的。
     */
    private int claimRefund(String outTradeNo) {
        UpdateWrapper<PayOrder> delivered = new UpdateWrapper<PayOrder>()
                .eq("out_trade_no", outTradeNo)
                .eq("status", "DELIVERED")
                .set("status", "REFUNDED");
        if (payOrderMapper.update(null, delivered) == 1) {
            return 1;
        }
        UpdateWrapper<PayOrder> pending = new UpdateWrapper<PayOrder>()
                .eq("out_trade_no", outTradeNo)
                .in("status", "INIT", "PAID")
                .set("status", "REFUNDED");
        if (payOrderMapper.update(null, pending) == 1) {
            return 2;
        }
        return 0;
    }

    /** 落一条退款流水（审计留痕）。同一 (wx_refund_id, ret_code) 只落一次，重复推送/重试不会产生多条。 */
    private void recordRefund(PayOrder order, String wxRefundId, String mchRefundId, String wxOrderId,
                              Integer refundFee, int retCode, String retMsg, Long succTs, int quotaReduced) {
        if (wxRefundId != null && !wxRefundId.isBlank()) {
            Long exists = payRefundMapper.selectCount(new QueryWrapper<PayRefund>()
                    .eq("wx_refund_id", wxRefundId)
                    .eq("ret_code", retCode));
            if (exists != null && exists > 0) {
                return;
            }
        }
        PayRefund r = new PayRefund();
        r.setOutTradeNo(order.getOutTradeNo());
        r.setOpenid(order.getOpenid());
        r.setWxRefundId(wxRefundId);
        r.setMchRefundId(mchRefundId);
        r.setWxOrderId(wxOrderId);
        r.setRefundFee(refundFee == null ? 0 : refundFee);
        r.setRetCode(retCode);
        r.setRetMsg(retMsg == null ? null : (retMsg.length() > 250 ? retMsg.substring(0, 250) : retMsg));
        r.setQuotaReduced(quotaReduced);
        r.setSuccAt(succTs == null || succTs <= 0
                ? null
                : LocalDateTime.ofEpochSecond(succTs, 0, ZoneOffset.ofHours(8)));
        r.setCreatedAt(LocalDateTime.now());
        try {
            payRefundMapper.insert(r);
        } catch (Exception e) {
            // 留痕失败不影响退款主流程（额度已回收、订单状态已改）
            log.warn("退款流水写入失败 order={}: {}", order.getOutTradeNo(), e.toString());
        }
    }

    /**
     * iOS「退款问询」事件（xpay_subscribe_ios_refund_query_notify）的应答决策。
     *
     * Apple 会在用户申请退款时连续问询 3 次，开发者需在 3 秒内给出意见，Apple 参考后做最终决定；
     * 最终批准后仍会推 xpay_refund_notify（那时才真正回收额度）。
     * 我们按官方建议给凭据：已发货 → 拒绝退款；未发货 → 建议退款。
     */
    public Map<String, Object> decideIosRefundQuery(JsonNode notify) {
        String payOrderId = firstNonBlank(text(notify, "pay_order_id"),
                text(notify, "MchOrderId"), text(notify, "OutTradeNo"));
        boolean delivered = false;
        if (payOrderId != null && !payOrderId.isBlank()) {
            PayOrder order = payOrderMapper.selectById(payOrderId);
            delivered = order != null && "DELIVERED".equals(order.getStatus());
        }
        log.info("iOS 退款问询 payOrderId={} 已发货={} -> {}", payOrderId, delivered,
                delivered ? "拒绝退款" : "建议退款");
        Map<String, Object> resp = new LinkedHashMap<>();
        if (delivered) {
            resp.put("result_code", 1);
            resp.put("result_info", "拒绝退款");
            resp.put("evidence", "该商品为虚拟内容（考试提醒额度包），支付成功后额度已即时发放到用户账户，数字内容一经发放不支持退款。");
        } else {
            resp.put("result_code", 0);
            resp.put("result_info", "建议退款");
            resp.put("evidence", "该订单尚未发货，用户未获得任何虚拟内容，建议退款。");
        }
        return resp;
    }

    // ==========================================================
    // 兜底查单
    // ==========================================================

    /**
     * 扫描近 6 小时内仍为 INIT 的订单，主动调 /xpay/query_order 查单补发。
     * 任何异常都不抛出（查单是兜底，不能影响主流程）。
     */
    public Map<String, Object> recheckPending() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (!enabled) {
            out.put("ok", false);
            out.put("msg", "支付未开通");
            return out;
        }
        LocalDateTime now = LocalDateTime.now();
        List<PayOrder> pending = payOrderMapper.selectList(new QueryWrapper<PayOrder>()
                .eq("status", "INIT")
                .eq("env", env)   // 只查当前环境的订单
                .ge("created_at", now.minusHours(6))
                .le("created_at", now.minusMinutes(1))
                .orderByAsc("created_at")
                .last("limit 50"));

        int checked = 0;
        int delivered = 0;
        for (PayOrder o : pending) {
            checked++;
            try {
                Boolean paid = queryPaid(o.getOutTradeNo(), o.getOpenid(), o.getEnv() == null ? 0 : o.getEnv());
                if (Boolean.TRUE.equals(paid) && deliverByQuery(o)) {
                    delivered++;
                }
            } catch (Exception e) {
                log.warn("查单异常 order={}: {}", o.getOutTradeNo(), e.toString());
            }
        }

        // ② 退款兜底：扫最近已发货的订单，若微信侧已退款（状态 5/8）则回收额度。
        //   商家后台退款 / iOS 退款的通知偶有丢失，这一步保证最终一致（不影响用户此时的使用）。
        List<PayOrder> deliveredOrders = payOrderMapper.selectList(new QueryWrapper<PayOrder>()
                .eq("status", "DELIVERED")
                .eq("env", env)
                .ge("delivered_at", now.minusHours(6))
                .orderByAsc("delivered_at")
                .last("limit 50"));
        int refunded = 0;
        for (PayOrder o : deliveredOrders) {
            try {
                if (sweepRefund(o)) {
                    refunded++;
                }
            } catch (Exception e) {
                log.warn("退款兜底查单异常 order={}: {}", o.getOutTradeNo(), e.toString());
            }
        }

        out.put("ok", true);
        out.put("checked", checked);
        out.put("delivered", delivered);
        out.put("refunded", refunded);
        return out;
    }

    /** 退款兜底：查单发现微信侧已退款（status=5 已退款 / 8 用户退款完成）则回收额度 */
    private boolean sweepRefund(PayOrder order) {
        Integer st = queryOrderStatus(order.getOutTradeNo(), order.getOpenid(),
                order.getEnv() == null ? 0 : order.getEnv());
        if (st == null || !isRefundedStatus(st)) {
            return false;
        }
        // claimRefund 返回 1 才表示「订单此刻确实处于已发货状态」且由本次占用
        if (claimRefund(order.getOutTradeNo()) != 1) {
            return false;
        }
        int delta = order.getQuotaDelta() == null ? 0 : order.getQuotaDelta();
        int reduced = 0;
        if (delta > 0) {
            quotaService.reducePaidQuota(order.getOpenid(), delta);
            reduced = delta;
        }
        recordRefund(order, null, null, null, order.getActualFen(), 0,
                "退款兜底查单回收（微信状态 " + st + "）", null, reduced);
        log.info("退款兜底生效：查单发现已退款 order={} 回收额度={}", order.getOutTradeNo(), reduced);
        return true;
    }

    /** 查单确认已支付后按同样口径发货（走同一 CAS，与推送通道并发安全） */
    private boolean deliverByQuery(PayOrder order) {
        int delta = order.getQuotaDelta() == null ? 0 : order.getQuotaDelta();
        return claimAndDeliver(order.getOutTradeNo(), order.getOpenid(), delta,
                null, null, null, "recheck");
    }

    /**
     * 查单核验：返回 Boolean.TRUE=已支付 / Boolean.FALSE=明确未支付 / null=无法确认。
     * 只有「传输层失败 / 返回体不合预期」才进入 5 分钟退避 —— 业务错误码说明通道是通的，
     * 退避反而会挡住其他订单的正常查单。
     */
    private Boolean queryPaid(String outTradeNo, String openid, int orderEnv) {
        Integer st = queryOrderStatus(outTradeNo, openid, orderEnv);
        if (st == null) {
            return null;
        }
        if (st < 0) {
            // 微信侧确认查无此单 => 从未支付。可安全判定为「明确未支付」，
            // 用于拒绝伪造的无签名推送（下单接口会把 outTradeNo 返给客户端）。
            log.info("查单确认未支付（微信侧无此订单）order={}", outTradeNo);
            return Boolean.FALSE;
        }
        return isPaidStatus(st);
    }

    /**
     * 查单返回原始 order.status：
     *   &gt;=0 = 微信侧订单状态（2 已支付待发货 / 3 发货中 / 4 已发货 / 5 已退款 / 8 用户退款完成 …）
     *   -1   = 微信侧无此订单（从未支付）
     *   null = 无法确认（通道异常 / 返回体不合预期，已进入退避）
     */
    private Integer queryOrderStatus(String outTradeNo, String openid, int orderEnv) {
        if (System.currentTimeMillis() < queryBackoffUntil) {
            return null;
        }
        try {
            JsonNode r = queryOrder(outTradeNo, openid, orderEnv);
            if (r == null) {
                backoff("无响应");
                return null;
            }
            int errcode = r.path("errcode").asInt(0);
            if (errcode == QUERY_ERR_ORDER_NOT_FOUND) {
                return -1;
            }
            if (errcode != 0) {
                log.warn("查单返回业务错误 order={} errcode={} errmsg={}",
                        outTradeNo, errcode, r.path("errmsg").asText(""));
                return null;
            }
            JsonNode st = r.path("order").path("status");
            if (!st.isNumber()) {
                backoff("返回体缺少 order.status");
                return null;
            }
            return st.asInt();
        } catch (Exception e) {
            log.warn("查单异常（云调用与 https 均不可用）: {}", e.toString());
            backoff(e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 诊断用：把「发给微信的请求体」和「微信原样应答」都返回，便于核对云调用通道是否
     * 转发了 POST 请求体、以及错误码的真实含义。仅在带正确 token 时由 PayController 暴露。
     */
    public Map<String, Object> queryOrderDebug(String outTradeNo) {
        Map<String, Object> out = new LinkedHashMap<>();
        PayOrder order = payOrderMapper.selectById(outTradeNo);
        int orderEnv = (order != null && order.getEnv() != null) ? order.getEnv() : env;
        String openid = order != null ? order.getOpenid() : null;

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("openid", openid);
        req.put("env", orderEnv);
        req.put("order_id", outTradeNo);
        out.put("localOrder", order == null ? null : Map.of(
                "status", String.valueOf(order.getStatus()),
                "openid", String.valueOf(order.getOpenid()),
                "env", orderEnv,
                "quotaDelta", String.valueOf(order.getQuotaDelta())));
        out.put("requestBody", req);

        if (openid == null) {
            out.put("error", "本地无此订单，无法取得 openid");
            return out;
        }
        try {
            String bodyJson = om.writeValueAsString(req);
            String paySig = PaySignUtil.hmacSha256Hex(currentAppKey(), URI_QUERY_ORDER + "&" + bodyJson);
            JsonNode r = postJson("http://api.weixin.qq.com" + URI_QUERY_ORDER + "?pay_sig=" + paySig, bodyJson);
            out.put("cloudCall", om.convertValue(r, Map.class));
        } catch (Exception e) {
            out.put("cloudCallError", e.toString());
        }
        return out;
    }

    /** order.status：2=已支付待发货 3=发货中 4=已发货（5~10 为退款/回收链路，不算正常支付） */
    private static boolean isPaidStatus(int status) {
        return status >= 2 && status <= 4;
    }

    /** order.status：5=订单已经退款 8=用户退款完成 */
    private static boolean isRefundedStatus(int status) {
        return status == ORDER_STATUS_REFUNDED || status == ORDER_STATUS_USER_REFUNDED;
    }

    private void backoff(String reason) {
        queryBackoffUntil = System.currentTimeMillis() + QUERY_BACKOFF_MS;
        log.warn("查单核验暂不可用（{}），{} 分钟内跳过查单核验", reason, QUERY_BACKOFF_MS / 60_000);
    }

    /**
     * 调 /xpay/query_order 查单。两条通道依次尝试：
     *   ① 云托管「开放接口服务」内网通道 http://api.weixin.qq.com（免鉴权、不走 TLS，
     *      不受容器内证书链校验失败影响；需在云托管令牌权限里配置 /xpay/query_order）
     *   ② 直连 https（access_token + pay_sig）——容器内通常因 PKIX 证书链不可用，仅作降级
     * pay_sig 必须放在 query string（官方明确要求，不是 header）。
     */
    private JsonNode queryOrder(String outTradeNo, String openid, int orderEnv) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("openid", openid);
        body.put("env", orderEnv);
        body.put("order_id", outTradeNo);
        String bodyJson = om.writeValueAsString(body);
        String paySig = PaySignUtil.hmacSha256Hex(currentAppKey(), URI_QUERY_ORDER + "&" + bodyJson);

        if (cloudCallFirst) {
            try {
                // 云调用通道已实测可用（能拿到微信的业务级应答），因此把应答原样返回，
                // 不再因业务错误码降级到容器内必然失败的 https 通道（降级会丢掉真实错误码，
                // 还会把「通道正常但订单不存在」误判成「通道不可用」）
                return postJson("http://api.weixin.qq.com" + URI_QUERY_ORDER + "?pay_sig=" + paySig, bodyJson);
            } catch (Exception e) {
                log.warn("云调用查单请求失败，降级直连 https: {}", e.toString());
            }
        }
        String url = "https://api.weixin.qq.com" + URI_QUERY_ORDER
                + "?access_token=" + tokenManager.get() + "&pay_sig=" + paySig;
        return postJson(url, bodyJson);
    }

    private JsonNode postJson(String url, String bodyJson) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(4))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return om.readTree(resp.body());
    }

    // ==========================================================
    // 工具
    // ==========================================================

    /** jscode2session：换 openid + session_key（用户态签名必需） */
    private Session code2session(String code) throws Exception {
        String query = String.format(
                "appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                appid, secret, code);
        String body;
        if (cloudCallFirst) {
            // 云调用通道：容器内直连 https 会因证书链校验失败（PKIX path building failed），
            // 平台要求走 http://api.weixin.qq.com 由云托管网关转发（与订阅消息推送同一条通道）
            try {
                body = get("http://api.weixin.qq.com/sns/jscode2session?" + query);
            } catch (Exception e) {
                log.warn("code2session 云调用通道异常，降级直连 https: {}", e.toString());
                body = get("https://api.weixin.qq.com/sns/jscode2session?" + query);
            }
        } else {
            body = get("https://api.weixin.qq.com/sns/jscode2session?" + query);
        }
        JsonNode node = om.readTree(body);
        if (!node.hasNonNull("session_key")) {
            throw new IllegalStateException("code2session 未返回 session_key: " + body);
        }
        Session s = new Session();
        s.openid = node.path("openid").asText(null);
        s.sessionKey = node.get("session_key").asText();
        return s;
    }

    private String get(String url) throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    /** T + 时间戳 + 4 位随机，长度 21，满足「每个订单号只能使用一次」 */
    private String genOrderNo() {
        return "T" + LocalDateTime.now().format(ORDER_NO_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private static Map<String, Object> fail(String code, String msg) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", false);
        out.put("code", code);
        out.put("msg", msg);
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull()) ? null : v.asText();
    }

    private static Integer intVal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull() || !v.isNumber()) ? null : v.asInt();
    }

    private static Long longVal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull() || !v.isNumber()) ? null : v.asLong();
    }

    /** 返回第一个非空白字符串；全空返回 null。用于兼容同一语义的不同字段名 */
    private static String firstNonBlank(String... vals) {
        if (vals == null) {
            return null;
        }
        for (String v : vals) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    /** 用户会话（openid + session_key） */
    private static class Session {
        String openid;
        String sessionKey;
    }
}
