const api = require('../../utils/api');
const buy = require('../../utils/buy');

const NODE_LABEL = {
  SIGNUP: '报名',
  TICKET: '准考证',
  EXAM: '考试',
  SCORE: '成绩',
  RETEST: '复试',
  VOLUNTEER: '志愿',
  ADMISSION: '录取',
  CUSTOM: '自定',
};

const DAY_MS = 24 * 60 * 60 * 1000;

/** 各节点类型的提醒点（考前第 N 天），与后端 remind.nodes-by-type 默认值保持一致 */
const REMIND_NODES = {
  SIGNUP: [3, 1, 0],
  TICKET: [7, 1, 0],
  EXAM: [1, 0],
  SCORE: [1, 0],
  RETEST: [1, 0],
  VOLUNTEER: [1, 0],
  ADMISSION: [1, 0],
  CUSTOM: [1, 0],
};

/** 推送时间点：后端 RemindJob 每天定时执行（默认 08:00） */
const PUSH_TIME = '08:00';

/** 把 "YYYY-MM-DD" 解析成本地零点的时间戳，避免时区偏移 */
function parseDate(s) {
  const p = String(s).split('-').map(Number);
  return new Date(p[0], p[1] - 1, p[2]).getTime();
}

/** 时间戳 -> "YYYY-MM-DD" */
function fmtDate(ts) {
  const d = new Date(ts);
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
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
    customNodes: [],
    subList: [],
    highlightId: 0,
    errorMsg: '',
    quotaText: '',
    quotaRemaining: 0,
    quotaLimit: 0,
    quotaUsed: 0,
    subscribedMap: {},
    // 自定义下拉刷新
    pullH: 0,
    pullText: '',
    pullReady: false,
    dragging: false,
    refreshing: false,
  },

  onLoad(options) {
    // 订阅消息推送会跳到 pages/index/index?examId=xxx，用来定位到具体节点
    this.setData({ highlightId: Number(options.examId) || 0 });
  },

  onShow() {
    this.load();
    this.loadQuota();
    this.loadSubscriptions();
  },

  /** 记录页面滚动位置：只在滚到顶时才允许触发下拉刷新 */
  onPageScroll(e) {
    this._scrollTop = e.scrollTop || 0;
  },

  // ================= 自定义下拉刷新（京东样式） =================

  onTouchStart(e) {
    if (this.data.refreshing) return;
    if ((this._scrollTop || 0) <= 0) {
      this._pullStartY = e.touches[0].clientY;
      this._pullActive = true;
      this.setData({ dragging: true });
    }
  },

  onTouchMove(e) {
    if (!this._pullActive || this.data.refreshing) return;
    const dy = e.touches[0].clientY - this._pullStartY;
    if (dy <= 0) {
      if (this.data.pullH !== 0) this.setData({ pullH: 0, pullText: '', pullReady: false });
      return;
    }
    // 阻尼 + 上限：拉得越远越费劲，最多 110px
    const h = Math.round(Math.min(dy * 0.55, 110));
    const ready = h >= 55;
    // 命中阈值变化或位移超过 2px 才 setData，减少渲染次数
    if (ready !== this.data.pullReady || Math.abs(h - this.data.pullH) >= 2) {
      this.setData({
        pullH: h,
        pullText: ready ? '松手更新' : '下拉刷新',
        pullReady: ready,
      });
    }
  },

  onTouchEnd() {
    if (!this._pullActive) return;
    this._pullActive = false;
    if (this.data.pullReady && !this.data.refreshing) {
      // 松手 → 进入刷新态：图标旋转，收起改为带过渡动画
      this.setData({
        dragging: false,
        pullReady: false,
        pullH: 60,
        pullText: '正在刷新…',
        refreshing: true,
      });
      Promise.all([this.load(), this.loadQuota(), this.loadSubscriptions()])
        .catch(() => {})
        .then(() => {
          setTimeout(() => {
            this.setData({ refreshing: false, pullH: 0, pullText: '' });
          }, 350);
        });
    } else {
      this.setData({ dragging: false, pullH: 0, pullText: '', pullReady: false });
    }
  },

  /** 已订阅的节点（examId -> remainTimes），用来把按钮渲染成「已订阅」 */
  loadSubscriptions() {
    return api.fetchSubscriptions()
      .then((r) => {
        if (!r || !r.ok) return;
        const map = {};
        (r.items || []).forEach((it) => { map[it.examId] = it.remainTimes; });
        this.setData({ subscribedMap: map }, () => this.buildSubList());
      })
      .catch((err) => console.error('fetchSubscriptions failed:', err));
  },

  /**
   * 消息清单：把已订阅的节点汇总成一份列表（官方 + 自定义），按考试日期正序排列。
   * 已结束的节点不显示——推送永远不会触发，订阅对用户已无意义。
   */
  buildSubList() {
    const map = this.data.subscribedMap;
    const nodes = this._allNodes || [];
    const list = nodes
      .filter((n) => !n.passed && map[n.id] > 0)
      .map((n) => {
        const days = daysLeft(n.examDate);
        // 还没错过的提醒点，按时间正序；每次推送消耗 1 次额度，
        // 剩余 N 次就列出接下来 N 个推送时间点（额度用完即止）
        const examTs = parseDate(n.examDate);
        const pushTimes = (REMIND_NODES[n.nodeType] || [1, 0])
          .filter((d) => d <= days)
          .sort((a, b) => a - b)
          .map((d) => `${fmtDate(examTs - d * DAY_MS)} ${PUSH_TIME}`);
        if (pushTimes.length === 0) pushTimes.push(`${n.examDate} ${PUSH_TIME}`);
        return Object.assign({}, n, {
          remainTimes: map[n.id],
          // 剩余 N 次就展示接下来 N 个推送时间点（每次推送消耗 1 次额度）
          pushTimes: pushTimes.slice(0, map[n.id]),
        });
      })
      .sort((a, b) => parseDate(a.examDate) - parseDate(b.examDate));
    this.setData({ subList: list });
  },

  /** 顶部显示设置提醒占用情况，方便用户知道还能加几个 */
  loadQuota() {
    return api.fetchQuota()
      .then((q) => {
        if (q && q.ok !== false && typeof q.remaining === 'number') {
          this.setData({
            quotaText: `已用 ${q.used}/${q.limit}，还可加 ${q.remaining} 个`,
            quotaRemaining: q.remaining,
            quotaLimit: q.limit,
            quotaUsed: q.used,
          });
        }
      })
      .catch((err) => console.error('fetchQuota failed:', err));
  },

  /** 加提醒：额度用完时引导购买额度包，否则直接进添加页 */
  onAdd() {
    if (this.data.quotaRemaining <= 0) {
      this.promptBuy();
      return;
    }
    wx.navigateTo({ url: '/pages/add/add' });
  },

  /** 额度用完 / 主动购买：确认后走购买流程 */
  onBuyQuota() {
    this.promptBuy();
  },

  promptBuy() {
    const full = this.data.quotaRemaining <= 0;
    wx.showModal({
      title: full ? '提醒额度已用完' : '提醒额度包',
      content: full
        ? `当前已用 ${this.data.quotaUsed}/${this.data.quotaLimit}，购买额度包可同时管理更多提醒（删除或过期的提醒会自动释放额度）`
        : `当前已用 ${this.data.quotaUsed}/${this.data.quotaLimit}，可购买额度包提升上限`,
      confirmText: '去购买',
      cancelText: '暂不',
      success: (r) => {
        if (r.confirm) {
          this.buyQuota();
        }
      },
    });
  },

  buyQuota() {
    buy.buyQuota().then((r) => {
      if (r && r.ok) {
        this.loadQuota();
        this.load();
      }
    });
  },

  /** 长按节点卡片：自建提醒可触发删除，官方节点无额外操作 */
  onNodeLongpress(e) {
    const { id, name, iscustom } = e.currentTarget.dataset;
    if (!iscustom) return;
    this.doDelete(id, name);
  },

  /** 删除自建提醒：二次确认后删除，释放额度 */
  doDelete(id, name) {
    wx.showModal({
      title: '删除提醒',
      content: `确定删除「${name}」吗？删除后不再推送提醒。`,
      confirmColor: '#e64340',
      success: (res) => {
        if (!res.confirm) {
          return;
        }
        api.deleteCustom(id)
          .then((r) => {
            if (r && r.ok) {
              const map = Object.assign({}, this.data.subscribedMap);
              delete map[id];
              this.setData({ subscribedMap: map }, () => this.buildSubList());
              wx.showToast({ title: '已删除', icon: 'success' });
              this.load();
              this.loadQuota();
            } else {
              wx.showToast({ title: (r && r.msg) || '删除失败', icon: 'none' });
            }
          })
          .catch((err) => {
            console.error('deleteCustom failed:', err);
            wx.showToast({ title: '删除失败，请重试', icon: 'none' });
          });
      },
    });
  },

  load() {
    this.setData({ loading: true, errorMsg: '' });
    return api.fetchExams()
      .then((list) => {
        const built = this.buildGroups(list || []);
        // 供「消息清单」按 examId 回查节点信息（官方 + 自定义）
        this._allNodes = built.custom.concat(built.official.reduce((acc, g) => acc.concat(g.nodes), []));
        this.setData({
          customNodes: built.custom,
          groups: built.official,
          loading: false,
        }, () => this.buildSubList());
      })
      .catch((err) => {
        console.error('fetchExams failed:', err);
        this.setData({
          loading: false,
          errorMsg: '加载失败，下拉重试',
        });
      });
  },

  /**
   * 分组：自建提醒（groupCode=custom）单独拆出来置顶，官方节点按 groupCode 分组。
   * 组内按 exam_date 升序。
   */
  buildGroups(list) {
    const custom = [];
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
        isCustom: !!item.isCustom,
      };

      if (node.isCustom || item.groupCode === 'custom') {
        custom.push(node);
        return;
      }

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

    const byDate = (a, b) => parseDate(a.examDate) - parseDate(b.examDate);
    custom.sort(byDate);
    Object.keys(map).forEach((k) => map[k].nodes.sort(byDate));

    return {
      custom,
      official: Object.keys(map).map((k) => map[k]),
    };
  },

  daysLabel(days) {
    if (days < 0) return '已结束';
    if (days === 0) return '今天';
    if (days === 1) return '明天';
    return `${days} 天后`;
  },

  /**
   * 点击节点整行（不再只点那个小按钮，按钮太小不好点）：
   * 已结束 → 提示无法订阅；
   * 已订阅 → 操作菜单：再订一次 / 取消订阅 /（自建）删除提醒；
   * 未订阅 → 直接走订阅授权。
   */
  onNodeTap(e) {
    const { id, name, iscustom, templateid: templateId } = e.currentTarget.dataset;
    const node = (this._allNodes || []).find((n) => n.id === id);
    if (node && node.passed) {
      wx.showToast({ title: '该节点已结束，无法订阅', icon: 'none' });
      return;
    }
    if (!templateId) {
      wx.showToast({ title: '缺少模板ID，请联系管理员', icon: 'none' });
      return;
    }
    if (this.data.subscribedMap[id] > 0) {
      this.openActions(id, name, iscustom, templateId);
      return;
    }
    this.doSubscribe(id, templateId);
  },

  /** 已订阅节点的操作菜单 */
  openActions(id, name, iscustom, templateId) {
    const items = ['再订一次（+1 条推送额度）', '取消订阅'];
    if (iscustom) {
      items.push('删除提醒');
    }
    wx.showActionSheet({
      itemList: items,
      success: (res) => {
        if (res.tapIndex === 0) {
          this.doSubscribe(id, templateId);
        } else if (res.tapIndex === 1) {
          this.doCancel(id, name);
        } else if (res.tapIndex === 2) {
          this.doDelete(id, name);
        }
      },
    });
  },

  /** 取消订阅：二次确认后删除该节点的推送额度 */
  doCancel(id, name) {
    wx.showModal({
      title: '取消订阅',
      content: `取消后不再收到「${name}」的提醒，确定吗？`,
      confirmText: '取消订阅',
      confirmColor: '#e64340',
      success: (res) => {
        if (!res.confirm) return;
        api.cancelSubscribe(id)
          .then((r) => {
            if (r && r.ok) {
              const map = Object.assign({}, this.data.subscribedMap);
              delete map[id];
              this.setData({ subscribedMap: map }, () => this.buildSubList());
              wx.showToast({ title: '已取消订阅', icon: 'success' });
            } else {
              wx.showToast({ title: (r && r.msg) || '取消失败，请重试', icon: 'none' });
            }
          })
          .catch((err) => {
            console.error('cancelSubscribe failed:', err);
            wx.showToast({ title: '取消失败，请重试', icon: 'none' });
          });
      },
    });
  },

  doSubscribe(id, templateId) {
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
              const map = Object.assign({}, this.data.subscribedMap);
              map[id] = r.remainTimes;
              this.setData({ subscribedMap: map }, () => this.buildSubList());
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
