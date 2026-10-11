/**
 * 订单中心（虚拟支付）。
 *
 * 微信要求「选购商品 - 下单 - 支付」类小程序提供统一的订单中心页，
 * 并在提审时登记主 path（pages/orders/orders，不带参数）。
 * 页面只读展示当前用户的额度包订单：openid 由云托管注入（X-WX-OPENID），
 * 无需登录态；进入页面不自动跳转，直接停留在订单列表。
 */

const api = require('../../utils/api');

/** 订单状态 -> 展示文案 */
const STATUS_TEXT = {
  INIT: '待支付',
  PAID: '处理中',
  DELIVERED: '已到账',
  CLOSED: '已关闭',
  REFUNDED: '已退款',
};

/** 分 -> 元（去掉末尾 .00），优先取微信回调的实付金额 */
function fen2yuan(fen) {
  const n = Number(fen);
  if (!isFinite(n) || n <= 0) return '0';
  return (n / 100).toFixed(2).replace(/\.00$/, '');
}

Page({
  data: {
    loading: true,
    orders: [],
  },

  onShow() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    console.log('[exam][orders] 拉取订单列表');
    this.setData({ loading: true });
    return api.myOrders()
      .then((res) => {
        const list = res && res.ok && Array.isArray(res.orders) ? res.orders : [];
        const orders = list.map((o) => ({
          outTradeNo: o.outTradeNo,
          name: o.goodsName || '提醒额度包',
          quota: o.quotaDelta,
          amount: fen2yuan(o.actualFen != null ? o.actualFen : o.priceFen),
          statusText: STATUS_TEXT[o.status] || o.status || '-',
          statusClass:
            o.status === 'DELIVERED' ? 'ok' : o.status === 'REFUNDED' ? 'refund' : '',
          time: String(o.createdAt || '').replace('T', ' ').slice(0, 16),
        }));
        this.setData({ orders, loading: false });
        console.log('[exam][orders] 订单数:', orders.length);
      })
      .catch((e) => {
        console.warn('[exam][orders] 拉取失败', e);
        this.setData({ loading: false });
        wx.showToast({ title: '加载失败，请下拉重试', icon: 'none' });
      });
  },
});
