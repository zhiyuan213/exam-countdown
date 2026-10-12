const api = require('../../utils/api');
const buy = require('../../utils/buy');

const pad = (n) => (n < 10 ? '0' + n : '' + n);
const fmt = (d) => d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());

Page({
  data: {
    name: '',
    date: '',
    minDate: '',
    maxDate: '',
    quota: null,
    templateId: '',
    submitting: false,
  },

  onLoad() {
    const now = new Date();
    // 当天 08:00（推送时刻）已过的话，选今天则当天推送收不到，默认日期落到明天
    const base = now.getHours() >= 8 ? new Date(now.getTime() + 24 * 3600 * 1000) : now;
    const max = new Date(base.getFullYear() + 5, base.getMonth(), base.getDate());
    this.setData({
      minDate: fmt(now),
      maxDate: fmt(max),
      date: fmt(base),
    });
    this.loadQuota();
    this.loadTemplate();
  },

  onShow() {
    this.loadQuota();
  },

  loadQuota() {
    api.fetchQuota()
      .then((q) => {
        if (q && q.ok !== false) {
          this.setData({ quota: q });
        }
      })
      .catch((err) => console.error('fetchQuota failed:', err));
  },

  /** 订阅消息模板 ID：官方节点都用同一个模板，从列表里取第一个即可 */
  loadTemplate() {
    api.fetchExams()
      .then((list) => {
        const hit = (list || []).find((x) => x.templateId);
        if (hit) {
          this.setData({ templateId: hit.templateId });
        }
      })
      .catch((err) => console.error('fetchExams failed:', err));
  },

  onNameInput(e) {
    this.setData({ name: e.detail.value });
  },

  onDateChange(e) {
    this.setData({ date: e.detail.value });
  },

  /**
   * 先当场申请订阅授权（必须由点击事件链发起），拿到结果再创建节点，
   * 这样「授权」和「登记推送额度」不会出现先建节点后授权失败的脏状态。
   */
  onSubmit() {
    const name = (this.data.name || '').trim();
    if (!name) {
      wx.showToast({ title: '请填写提醒名称', icon: 'none' });
      return;
    }
    if (name.length > 20) {
      wx.showToast({ title: '名称不能超过 20 个字', icon: 'none' });
      return;
    }
    if (!this.data.date) {
      wx.showToast({ title: '请选择日期', icon: 'none' });
      return;
    }
    if (this.data.submitting) {
      return;
    }

    const tplId = this.data.templateId;
    if (api.IS_MOCK) {
      // mock 模式：跳过真实订阅授权弹窗（开发者工具里 requestSubscribeMessage
      // 的 success/fail 回调经常不触发，会导致 create 不执行、额度不更新），
      // 直接创建节点，专注验证额度与列表逻辑。
      console.log('[exam][add] mock 模式：跳过订阅授权弹窗');
      this.create(false);
      return;
    }
    if (tplId) {
      // 兜底：开发者工具里 requestSubscribeMessage 偶发 success/fail 都不回调，
      // 5 秒没结果就视为未授权，照常创建节点，避免「点了没反应」。
      let settled = false;
      const guard = setTimeout(() => {
        if (!settled) {
          settled = true;
          console.warn('[exam][add] 授权 5s 无回调，按未授权继续创建');
          this.create(false);
        }
      }, 5000);
      const finish = (authorized) => {
        if (settled) return;
        settled = true;
        clearTimeout(guard);
        console.log(`[exam][add] 订阅授权结果=${authorized ? 'accept' : '拒绝/失败'}`);
        this.create(authorized);
      };
      wx.requestSubscribeMessage({
        tmplIds: [tplId],
        success: (res) => finish(res[tplId] === 'accept'),
        fail: (err) => {
          console.warn('[exam][add] 授权调用失败:', err);
          finish(false);
        },
      });
    } else {
      this.create(false);
    }
  },

  create(authorized) {
    this.setData({ submitting: true });
    const name = (this.data.name || '').trim();
    console.log(`[exam][add] 创建提醒 name=${name} date=${this.data.date} 已授权=${authorized}`);

    api.createCustom({ name, examDate: this.data.date })
      .then((res) => {
        if (!res || !res.ok) {
          if (res && res.code === 'QUOTA_EXCEEDED') {
            console.log('[exam][add] 额度不足，引导购买');
            this.promptBuy();
            return null;
          }
          console.warn('[exam][add] 创建失败:', (res && res.msg) || res);
          wx.showModal({
            title: '添加失败',
            content: (res && res.msg) || '请稍后重试',
            showCancel: false,
          });
          return null;
        }
        console.log('[exam][add] 创建成功 id=' + res.item.id, '剩余额度=' + JSON.stringify(res.quota));
        this.setData({ quota: res.quota });
        if (authorized) {
          return api.subscribe(res.item.id)
            .then(() => {
              console.log('[exam][add] 订阅登记成功 id=' + res.item.id);
              return this.done('已添加，并已订阅提醒');
            })
            .catch((err) => {
              console.warn('[exam][add] 订阅登记失败 id=' + res.item.id, err);
              return this.done('已添加，订阅登记失败，可稍后在首页补订');
            });
        }
        this.done('已添加，未订阅推送');
        return null;
      })
      .catch((err) => {
        console.error('[exam][add] createCustom 异常:', err);
        wx.showToast({ title: '网络异常，请重试', icon: 'none' });
      })
      .then(() => this.setData({ submitting: false }));
  },

  /** 额度不足：引导购买额度包（购买流程与首页共用 utils/buy.js） */
  promptBuy() {
    const q = this.data.quota || {};
    wx.showModal({
      title: '提醒额度已用完',
      content: `当前已用 ${q.used}/${q.limit}，购买额度包可同时管理更多提醒`,
      confirmText: '去购买',
      cancelText: '取消',
      success: (r) => {
        if (!r.confirm) return;
        buy.buyQuota().then((res) => {
          if (res && res.ok) {
            this.loadQuota();
          }
        });
      },
    });
  },

  done(msg) {
    wx.showToast({ title: msg, icon: 'none', duration: 1500 });
    setTimeout(() => wx.navigateBack(), 1200);
  },

  onShareAppMessage() {
    return {
      title: '考试倒计时 · 重要考试日期，一个都不漏',
      path: '/pages/index/index?from=share',
      imageUrl: '/images/share-cover.jpg',
    };
  },

  /** 朋友圈分享：不支持自定义路径，imageUrl 需 1:1 */
  onShareTimeline() {
    return {
      title: '考试倒计时 · 重要考试日期，一个都不漏',
      query: 'from=timeline',
      imageUrl: '/images/share-timeline.jpg',
    };
  },
});
