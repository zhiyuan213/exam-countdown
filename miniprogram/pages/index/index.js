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
const PUSH_TIME = '8:00';

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

/**
 * 还没错过的提醒点（考前第 N 天，按推送时间先后排序）。
 * 提醒点按节点类型固定配置（如自建 = 前 1 天 + 当天），与后端 RemindJob 同步；
 * 因此「再订」多攒的额度超过提醒点数后就再也消费不掉 —— 展示和入口都要按有效次数算。
 */
function futurePushDays(nodeType, examDate) {
  const days = daysLeft(examDate);
  const list = (REMIND_NODES[nodeType] || [1, 0])
    .filter((d) => d <= days)
    .sort((a, b) => b - a); // 时间正序：D-7 早于 D-1 早于 D-0
  // 推送时刻（08:00）已过就不再是未来时点：d == days 的时点推送日就是今天，
  // 08:00 后从「未来推送」中剔除（无论它是 D-0 还是 D-1 等落在今天的点）
  if (new Date().getHours() >= 8) {
    return list.filter((d) => d < days);
  }
  return list;
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
    pullRotate: 0,
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
      if (this.data.pullH !== 0) {
        this.setData({ pullH: 0, pullRotate: 0, pullText: '', pullReady: false });
      }
      return;
    }
    // 阻尼 + 上限：拉得越远越费劲，最多 110px
    const h = Math.round(Math.min(dy * 0.55, 110));
    const ready = h >= 55;
    // 命中阈值变化或位移超过 2px 才 setData，减少渲染次数
    if (ready !== this.data.pullReady || Math.abs(h - this.data.pullH) >= 2) {
      // 图标旋转角度与下拉区高度（即文字距顶部的高度）严格线性相关：
      // pullRotate = 高度 h / 110 × 360°，高度越高角度越大，拉满 110px 时正好 360°（整圈）。
      // 方向：下拉 = 顺时针（正角度）；松手后角度收回 0°，数值递减 → 视觉上是逆时针转回正位。
      const t = Math.min(1, h / 110);
      this.setData({
        pullH: h,
        pullRotate: Math.round(t * 360),
        pullText: ready ? '松手更新' : '下拉刷新',
        pullReady: ready,
      });
    }
  },

  onTouchEnd() {
    if (!this._pullActive) return;
    this._pullActive = false;
    if (this.data.pullReady && !this.data.refreshing) {
      // 松手 → 进入刷新态：角度从当前值逆时针收回 0° 回正（拉满时即逆时针转满一圈回到起点）；
      // 刷新中图标不再自转，只用文字「正在刷新」做反馈
      this.setData({
        dragging: false,
        pullReady: false,
        pullRotate: 0,
        pullH: 66,
        pullText: '正在刷新',
        refreshing: true,
      });
      Promise.all([this.load(), this.loadQuota(), this.loadSubscriptions()])
        .catch(() => {})
        .then(() => {
          setTimeout(() => {
            // 停止旋转：图标带过渡转回正位，回正后再收起下拉区
            this.setData({ refreshing: false, pullRotate: 0 });
            setTimeout(() => {
              this.setData({ pullH: 0, pullText: '' });
            }, 320);
          }, 350);
        });
    } else {
      // 未达阈值松手：解除 dragging 让过渡生效，图标转回正位后再收起下拉区
      this.setData({ dragging: false, pullReady: false, pullRotate: 0 });
      setTimeout(() => {
        this.setData({ pullH: 0, pullText: '' });
      }, 340);
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
        // 列出的时间点 = 未来真正会推送的时刻。剩余额度超过提醒点数时，多出的
        // 部分永远消费不掉，所以只展示 min(剩余, 提醒点数) 条日期，
        // 与实际可收到的推送严格一致（界面上不显示次数，只列时间）。
        const futureDays = futurePushDays(n.nodeType, n.examDate);
        const remain = map[n.id];
        const effectiveTimes = Math.min(remain, futureDays.length);
        const examTs = parseDate(n.examDate);
        const pushTimes = futureDays
          .map((d) => `${fmtDate(examTs - d * DAY_MS)} ${PUSH_TIME}`)
          .slice(0, effectiveTimes);
        if (pushTimes.length === 0) pushTimes.push('今日推送时间已过');
        return Object.assign({}, n, {
          remainTimes: remain,
          effectiveTimes,
          pushTimes,
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
   * 已订阅 → 操作菜单：再订 1 条提醒 / 取消订阅 /（自建）删除提醒；
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

  /**
   * 已订阅节点的操作菜单。
   *
   * 文案避免「推送额度」这类内部说法：微信「一次性订阅」= 授权一次只能推一条，
   * 所以这一步的真实含义就是「多订一条提醒」，用户看不出这层意思，点后再补一句弹窗说明。
   */
  openActions(id, name, iscustom, templateId) {
    // 剩余额度已覆盖全部未来推送时点（或没有未来时点）时，再订毫无意义，不出现该选项
    const node = (this._allNodes || []).find((n) => n.id === id);
    const futureDays = node ? futurePushDays(node.nodeType, node.examDate) : [];
    const remain = this.data.subscribedMap[id] || 0;
    const canResub = futureDays.length > 0 && remain < futureDays.length;
    const items = [];
    const actions = [];
    if (canResub) {
      items.push('再订 1 条提醒');
      actions.push(() => this.onResubscribeTap(id, name, templateId));
    }
    if (iscustom) {
      // 自建节点不提供「取消订阅」，停提醒就走「删除提醒」整行删除
      items.push('删除提醒');
      actions.push(() => this.doDelete(id, name));
    } else {
      // 官方节点无法删除，只能取消订阅（节点保留，可重新订阅）
      items.push('取消订阅');
      actions.push(() => this.doCancel(id, name));
    }
    wx.showActionSheet({
      itemList: items,
      success: (res) => {
        const fn = actions[res.tapIndex];
        if (fn) fn();
      },
    });
  },

  /** 取消订阅：二次确认后删除该节点的推送额度（剩余授权一并丢弃，清单中移除，节点保留可重订） */
  doCancel(id, name) {
    const remain = this.data.subscribedMap[id] || 0;
    const tip = remain > 0
      ? `取消后不再收到「${name}」的提醒，剩余的 ${remain} 次提醒额度也会一并清除（节点保留，可随时重新订阅）。确定吗？`
      : `取消后不再收到「${name}」的提醒，确定吗？`;
    wx.showModal({
      title: '取消订阅',
      content: tip,
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

  /**
   * 「再订 1 条提醒」入口：先判断再订还有没有意义。
   * 提醒点按节点类型固定（如自建 = 前 1 天 + 当天），剩余额度已覆盖全部未来
   * 提醒点时，再订拿到的授权永远消费不掉，只会让「剩余次数」虚高 —— 直接说明，不拉授权。
   */
  onResubscribeTap(id, name, templateId) {
    const node = (this._allNodes || []).find((n) => n.id === id);
    const remain = this.data.subscribedMap[id] || 0;
    const futureDays = node ? futurePushDays(node.nodeType, node.examDate) : [];
    if (futureDays.length === 0) {
      // 没有未来推送时点了（提醒点全部过去），再订的授权永远消费不掉
      wx.showModal({
        title: '没有可再订的提醒',
        content: `「${name}」的提醒时点都已过去，再订也不会再收到提醒。`,
        showCancel: false,
        confirmText: '知道了',
      });
      return;
    }
    if (remain >= futureDays.length) {
      const examTs = parseDate(node.examDate);
      const dates = futureDays.map((d) => fmtDate(examTs - d * DAY_MS)).join('、');
      wx.showModal({
        title: '提醒次数已足够',
        content: `「${name}」接下来最多还会提醒 ${futureDays.length} 次（${dates} 各一次），当前剩余次数已足够，再订也不会多收提醒。`,
        showCancel: false,
        confirmText: '知道了',
      });
      return;
    }
    this.confirmResubscribe(id, name, templateId);
  },

  /**
   * 再订一条提醒前的说明弹窗。
   * 微信规定「授权一次只能推一条」，所以每多要一条提醒都得重新拉起一次授权；
   * 用户看到系统授权弹窗时常常不知道自己在授权什么，这里先讲清楚再走授权。
   */
  confirmResubscribe(id, name, templateId) {
    wx.showModal({
      title: '再订 1 条提醒',
      content: `微信要求每条提醒都单独授权一次。同意后，你会多收到 1 条「${name}」的提醒。`,
      confirmText: '去授权',
      cancelText: '再想想',
      success: (res) => {
        if (res.confirm) {
          this.doSubscribe(id, templateId);
        }
      },
    });
  },

  doSubscribe(id, templateId) {
    wx.requestSubscribeMessage({
      tmplIds: [templateId],
      success: (res) => {
        if (res[templateId] !== 'accept') {
          // 用户在授权弹窗点了「取消」：静默忽略，不弹提示
          return;
        }
        api.subscribe(id)
          .then((r) => {
            if (r && r.ok) {
              const map = Object.assign({}, this.data.subscribedMap);
              map[id] = r.remainTimes;
              this.setData({ subscribedMap: map }, () => this.buildSubList());
              // 提示有效次数（与消息清单口径一致），避免「剩余 6 次」这种兑现不了的数字
              const node = (this._allNodes || []).find((n) => n.id === id);
              const futureDays = node ? futurePushDays(node.nodeType, node.examDate) : [];
              const effective = futureDays.length
                ? Math.min(r.remainTimes, futureDays.length)
                : 0;
              wx.showToast({
                title: effective > 0
                  ? `已订阅，还可提醒 ${effective} 次`
                  : '已订阅，今天的推送时间已过',
                icon: 'none',
              });
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
