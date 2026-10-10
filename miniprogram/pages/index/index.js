const api = require('../../utils/api');

const NODE_LABEL = {
  SIGNUP: '报名',
  TICKET: '准考证',
  EXAM: '考试',
  SCORE: '成绩',
  RETEST: '复试',
  VOLUNTEER: '志愿',
  ADMISSION: '录取',
};

const DAY_MS = 24 * 60 * 60 * 1000;

/** 把 "YYYY-MM-DD" 解析成本地零点的时间戳，避免时区偏移 */
function parseDate(s) {
  const p = String(s).split('-').map(Number);
  return new Date(p[0], p[1] - 1, p[2]).getTime();
}

/** 从今天零点起算的剩余天数；今天 = 0，已过 = 负数 */
function daysLeft(dateStr) {
  const today = new Date();
  const t0 = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
  return Math.round((parseDate(dateStr) - t0) / DAY_MS);
}

Page({
  data: {
    loading: true,
    groups: [],
    highlightId: 0,
    errorMsg: '',
  },

  onLoad(options) {
    // 订阅消息推送会跳到 pages/index/index?examId=xxx，用来定位到具体节点
    this.setData({ highlightId: Number(options.examId) || 0 });
  },

  onShow() {
    this.load();
  },

  onPullDownRefresh() {
    this.load().then(() => wx.stopPullDownRefresh());
  },

  load() {
    this.setData({ loading: true, errorMsg: '' });
    return api.fetchExams()
      .then((list) => {
        this.setData({
          groups: this.buildGroups(list || []),
          loading: false,
        });
      })
      .catch((err) => {
        console.error('fetchExams failed:', err);
        this.setData({
          loading: false,
          errorMsg: '加载失败，下拉重试',
        });
      });
  },

  /** 按 groupCode 分组，组内按 sort 升序 */
  buildGroups(list) {
    const map = {};
    list.forEach((item) => {
      const days = daysLeft(item.examDate);
      const node = {
        id: item.id,
        name: item.name,
        examDate: item.examDate,
        templateId: item.templateId || '',
        nodeType: item.nodeType || 'EXAM',
        nodeLabel: NODE_LABEL[item.nodeType] || '节点',
        days,
        daysLabel: this.daysLabel(days),
        urgent: days >= 0 && days <= 7,
        passed: days < 0,
      };
      const key = item.groupCode || 'other';
      if (!map[key]) {
        map[key] = {
          groupCode: key,
          groupName: item.groupName || '其他',
          nodes: [],
        };
      }
      map[key].nodes.push(node);
    });
    Object.keys(map).forEach((k) => {
      map[k].nodes.sort((a, b) => parseDate(a.examDate) - parseDate(b.examDate));
    });
    return Object.keys(map).map((k) => map[k]);
  },

  daysLabel(days) {
    if (days < 0) return '已结束';
    if (days === 0) return '今天';
    if (days === 1) return '明天';
    return `${days} 天后`;
  },

  /** 点击「订阅提醒」：先请求微信订阅授权，再向后端登记额度 */
  onSubscribe(e) {
    const { id, name, templateid: templateId } = e.currentTarget.dataset;
    if (!templateId) {
      wx.showToast({ title: '缺少模板ID，请联系管理员', icon: 'none' });
      return;
    }

    wx.requestSubscribeMessage({
      tmplIds: [templateId],
      success: (res) => {
        if (res[templateId] !== 'accept') {
          wx.showToast({ title: '未授权，无法收到提醒', icon: 'none' });
          return;
        }
        api.subscribe(id)
          .then((r) => {
            if (r && r.ok) {
              wx.showToast({ title: `已订阅，剩余 ${r.remainTimes} 次`, icon: 'none' });
            } else {
              wx.showToast({ title: (r && r.msg) || '订阅失败', icon: 'none' });
            }
          })
          .catch((err) => {
            console.error('subscribe failed:', err);
            wx.showToast({ title: '订阅失败，请重试', icon: 'none' });
          });
      },
      fail: (err) => {
        console.error('requestSubscribeMessage failed:', err);
        wx.showToast({ title: '订阅授权失败', icon: 'none' });
      },
    });
  },
});
