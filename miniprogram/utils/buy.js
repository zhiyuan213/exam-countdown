/**
 * 额度包购买流程（虚拟支付二期），首页与「添加提醒」页共用。
 *
 * 完整链路：
 *   1. 拉商品列表（后端 /api/pay/goods）
 *   2. 弹菜单让用户选额度包
 *   3. wx.login() 取一次性 code —— 用户态签名 signature 需要后端用 code 换 session_key
 *   4. POST /api/pay/order 让后端组装 signData 并双签名（AppKey 只在服务端，绝不下发）
 *   5. wx.requestVirtualPayment 拉起微信支付
 *   6. 前端 success 只算「弱确认」，必须回后端查订单状态；发货由微信推送驱动，
 *      后端异步到账，所以这里轮询几次，没到账就提示稍后自动到账（不会丢单）
 *
 * 失败语义：用户主动取消（errCode=-2）不算错误，静默返回；其余失败都会 toast。
 */

const api = require('./api');

/** 分 -> 元（去掉末尾 .00） */
function fmtYuan(fen) {
  return (Number(fen) / 100).toFixed(2).replace(/\.00$/, '');
}

/** 拉商品并让用户选一个 */
function pickGoods() {
  return api.fetchGoods()
    .then((res) => {
      if (!res || !res.ok) {
        throw new Error((res && res.msg) || '获取额度包失败');
      }
      if (!res.enabled || !res.goods || !res.goods.length) {
        throw new Error('支付功能尚未开通，敬请期待');
      }
      return res.goods;
    })
    .then((goods) => new Promise((resolve, reject) => {
      wx.showActionSheet({
        itemList: goods.slice(0, 6).map((g) => `${g.name}　¥${fmtYuan(g.priceFen)}`),
        success: (r) => resolve(goods[r.tapIndex]),
        fail: () => reject({ cancelled: true }),
      });
    }));
}

/** wx.login 取 code */
function loginCode() {
  return new Promise((resolve, reject) => {
    wx.login({
      success: (r) => (r && r.code ? resolve(r.code) : reject(new Error('获取登录凭证失败，请重试'))),
      fail: () => reject(new Error('获取登录凭证失败，请重试')),
    });
  });
}

/** 拉起微信虚拟支付 */
function requestPay(order) {
  return new Promise((resolve, reject) => {
    if (typeof wx.requestVirtualPayment !== 'function') {
      reject(new Error('当前微信版本不支持虚拟支付，请升级微信后重试'));
      return;
    }
    wx.requestVirtualPayment({
      signData: order.signData,
      paySig: order.paySig,
      signature: order.signature,
      mode: order.mode,
      success: () => resolve(),
      fail: (err) => reject(err || { errCode: -1, errMsg: '支付失败' }),
    });
  });
}

/** 支付后回后端确认发货结果（轮询 3 次，每次间隔 1.2s） */
function confirmDelivered(orderId, times) {
  const left = typeof times === 'number' ? times : 3;
  return api.queryPayOrder(orderId)
    .then((res) => {
      if (res && res.ok && res.delivered) {
        return true;
      }
      if (left <= 1) {
        return false;
      }
      return new Promise((r) => setTimeout(r, 1200)).then(() => confirmDelivered(orderId, left - 1));
    })
    .catch(() => false);
}

/** 真实的支付 + 到账确认；返回 { ok, delivered } */
function payReal(goods) {
  let orderId = '';
  return loginCode()
    .then((code) => api.createPayOrder({ goodsId: goods.goodsId, code }))
    .then((res) => {
      if (!res || !res.ok) {
        throw new Error((res && res.msg) || '下单失败，请稍后重试');
      }
      orderId = res.orderId;
      return requestPay(res);
    })
    .then(() => confirmDelivered(orderId))
    .then((delivered) => ({ ok: true, delivered }));
}

/** mock 模式：跳过微信支付，直接让后端（mock）确认到账，方便本地验证 UI 与额度 */
function payMock(goods) {
  return api.createPayOrder({ goodsId: goods.goodsId, code: 'mock-code' })
    .then((res) => {
      if (!res || !res.ok) {
        throw new Error((res && res.msg) || '下单失败');
      }
      return confirmDelivered(res.orderId, 1).then((delivered) => ({ ok: true, delivered }));
    });
}

/**
 * 购买入口。resolve 的结果：
 *   { ok: true, delivered: true }  支付成功且额度已到账
 *   { ok: true, delivered: false } 支付成功，到账确认中（后端异步发货）
 *   { ok: false, cancelled: true } 用户主动取消
 *   { ok: false }                  其他失败（已 toast）
 * 调用方只需在 ok 时刷新额度即可。
 */
function buyQuota() {
  return pickGoods()
    .then((goods) => (api.IS_MOCK ? payMock(goods) : payReal(goods)))
    .then((r) => {
      wx.showToast({
        title: r.delivered ? '支付成功，额度已到账' : '支付成功，额度稍后到账',
        icon: 'none',
        duration: 2000,
      });
      return r;
    })
    .catch((err) => {
      if (err && err.cancelled) {
        return { ok: false, cancelled: true };
      }
      // 支付里用户点「取消」微信返回 errCode = -2
      if (err && (err.errCode === -2 || err.errno === -2)) {
        return { ok: false, cancelled: true };
      }
      const msg = (err && (err.msg || err.errMsg)) || '支付失败，请稍后重试';
      wx.showToast({ title: msg, icon: 'none', duration: 2000 });
      console.error('[buy] 购买失败:', err);
      return { ok: false };
    });
}

module.exports = { buyQuota, fmtYuan };
