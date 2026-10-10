const api = require('../../utils/api');

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
    const today = new Date();
    const max = new Date(today.getFullYear() + 5, today.getMonth(), today.getDate());
    this.setData({
      minDate: fmt(today),
      maxDate: fmt(max),
      date: fmt(today),
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
      console.log('[add] mock mode: skip subscribe dialog');
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
          console.warn('requestSubscribeMessage timeout, create without auth');
          this.create(false);
        }
      }, 5000);
      const finish = (authorized) => {
        if (settled) return;
        settled = true;
        clearTimeout(guard);
        this.create(authorized);
      };
      wx.requestSubscribeMessage({
        tmplIds: [tplId],
        success: (res) => finish(res[tplId] === 'accept'),
        fail: (err) => {
          console.warn('requestSubscribeMessage failed:', err);
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

    api.createCustom({ name, examDate: this.data.date })
      .then((res) => {
        if (!res || !res.ok) {
          wx.showModal({
            title: '添加失败',
            content: (res && res.msg) || '请稍后重试',
            showCancel: false,
          });
          return null;
        }
        this.setData({ quota: res.quota });
        if (authorized) {
          return api.subscribe(res.item.id)
            .then(() => this.done('已添加，并已订阅提醒'))
            .catch(() => this.done('已添加，订阅登记失败，可稍后在首页补订'));
        }
        this.done('已添加，未订阅推送');
        return null;
      })
      .catch((err) => {
        console.error('createCustom failed:', err);
        wx.showToast({ title: '网络异常，请重试', icon: 'none' });
      })
      .then(() => this.setData({ submitting: false }));
  },

  done(msg) {
    wx.showToast({ title: msg, icon: 'none', duration: 1500 });
    setTimeout(() => wx.navigateBack(), 1200);
  },
});
