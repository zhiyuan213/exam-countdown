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

/**
 * 统一日志前缀：真机调试/开发者工具里搜 "[exam]" 就能过滤出本小程序的流程日志。
 * 排查思路：先看 [exam][tap] 知道点了谁，再看 [exam][auth] 授权结果，
 * 最后看 [exam][api] 服务端登记结果 —— 三段对得上就是用户操作问题，对不上是代码/接口问题。
 */
function log(tag, ...args) {
  console.log(`[exam][${tag}]`, ...args);
}

/** 各节点类型的提醒点（考前第 N 天），与后端 remind.nodes-by-type 默认值保持一致 */
const REMIND_NODES = {
  SIGNUP: [2, 1, 0],
  TICKET: [2, 1, 0],
  EXAM: [2, 1, 0],
  SCORE: [2, 1, 0],
  RETEST: [2, 1, 0],
  VOLUNTEER: [2, 1, 0],
  ADMISSION: [2, 1, 0],
  CUSTOM: [2, 1, 0],
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
 * 提醒点按节点类型固定配置（统一为当天/前1天/前2天），与后端 RemindJob 同步；
 * 因此「再订」多攒的额度超过提醒点数后就再也消费不掉 —— 展示和入口都要按有效次数算。
 */
function futurePushDays(nodeType, examDate) {
  const days = daysLeft(examDate);
  const list = (REMIND_NODES[nodeType] || [2, 1, 0])
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
        if (!r || !r.ok) {
          log('subs', '订阅列表接口返回异常', r);
          return;
        }
        const map = {};
        (r.items || []).forEach((it) => { map[it.examId] = it.remainTimes; });
        log('subs', `已订阅 ${(r.items || []).length} 个节点`, JSON.stringify(map));
        this.setData({ subscribedMap: map }, () => this.buildSubList());
      })
      .catch((err) => console.error('[exam][subs] fetchSubscriptions 失败:', err));
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
        // 列出的时间点 = 未来真正会推送的时刻。推送额度优先保证靠近截止日的
        // 提醒（订 1 次只推截止当天，订 2 次推前一天+当天…），所以展示的是
        // min(剩余, 提醒点数) 个「离截止日最近」的时点，按离截止日由近到远排列。
        const futureDays = futurePushDays(n.nodeType, n.examDate);
        const remain = map[n.id];
        const effectiveTimes = Math.min(remain, futureDays.length);
        const examTs = parseDate(n.examDate);
        const pushTimes = futureDays
          .slice(futureDays.length - effectiveTimes) // 取离截止日最近的 N 个（真正会推送的），本身就是时间正序
          .map((d) => `${fmtDate(examTs - d * DAY_MS)} ${PUSH_TIME}`);
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
      .catch((err) => console.error('[exam][quota] fetchQuota 失败:', err));
  },

  /** 加提醒：额度用完时引导购买额度包，否则直接进添加页 */
  onAdd() {
    if (this.data.quotaRemaining <= 0) {
      this.promptBuy();
      return;
    }
    wx.navigateTo({ url: '/pages/add/add' });
  },

  /** 订单中心：查看额度包订单（微信要求交易类小程序提供订单中心页） */
  onOrders() {
    console.log('[exam][tap] 打开订单中心');
    wx.navigateTo({ url: '/pages/orders/orders' });
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
              log('delete', `删除自建提醒成功「${name}」id=${id}`);
              this.setData({ subscribedMap: map }, () => this.buildSubList());
              wx.showToast({ title: '已删除', icon: 'success' });
              this.load();
              this.loadQuota();
            } else {
              log('delete', `删除自建提醒被拒「${name}」id=${id} msg=${(r && r.msg) || ''}`);
              wx.showToast({ title: (r && r.msg) || '删除失败', icon: 'none' });
            }
          })
          .catch((err) => {
            console.error('[exam][delete] 删除自建提醒异常:', id, err);
            wx.showToast({ title: '删除失败，请重试', icon: 'none' });
          });
      },
    });
  },

  // ================= 分组批量订阅 / 批量取消 =================

  _groupNodes(code) {
    const group = (this.data.groups || []).find((g) => g.groupCode === code);
    return (group && group.nodes) || [];
  },

  /**
   * 批量订阅：把分组里「未过期、未订阅」的节点都订阅上，已订阅的不动。
   * 微信一次性订阅必须逐条授权，所以会依次弹授权确认；若用户勾选
   * 「总是保持以上选择，不再询问」，后续的授权会静默通过，不再弹窗。
   * 先把所有授权弹完，再统一登记额度，避免网络请求打断授权链。
   */
  onBatchSubscribe(e) {
    const code = e.currentTarget.dataset.code;
    const group = (this.data.groups || []).find((g) => g.groupCode === code);
    if (!group) return;
    const all = this._groupNodes(code);
    const targets = all.filter(
      (n) => !n.passed && n.templateId && !(this.data.subscribedMap[n.id] > 0)
    );
    log('batch', `点批量订阅「${group.groupName}」：分组内 ${all.length} 个节点`,
      `待订阅 ${targets.length}`,
      `已结束 ${all.filter((n) => n.passed).length}`,
      `已订阅 ${all.filter((n) => this.data.subscribedMap[n.id] > 0).length}`,
      `缺模板 ${all.filter((n) => !n.passed && !n.templateId).length}`);
    if (targets.length === 0) {
      log('batch', '→ 没有需要订阅的节点');
      wx.showToast({ title: '没有需要订阅的节点', icon: 'none' });
      return;
    }
    // 系统授权弹窗只显示模板名（所有节点共用一个模板时长得一样），
    // 所以在确认弹窗里把订阅顺序列出来，用户按序号对照即可知道当前授权的是谁。
    // 用页面内自定义弹窗而不是 showModal：后者的 \n 在部分机型不换行。
    this._bmConfirmCb = () => {
        const accepted = [];
        log('batch', `开始批量订阅「${group.groupName}」共 ${targets.length} 个`,
          targets.map((n) => n.name).join(' / '));
        const step = (i) => {
          if (i >= targets.length) {
            if (accepted.length === 0) {
              log('batch', '全部授权未通过，结束');
              wx.showToast({ title: '没有完成任何订阅', icon: 'none' });
              return;
            }
            log('batch', `授权通过 ${accepted.length}/${targets.length}，开始登记额度`);
            wx.showLoading({ title: '订阅中…', mask: true });
            Promise.all(
              accepted.map((node) =>
                api.subscribe(node.id)
                  .then((r) => (r && r.ok ? r : null))
                  .catch(() => null)
              )
            ).then((results) => {
              wx.hideLoading();
              const map = Object.assign({}, this.data.subscribedMap);
              let count = 0;
              // 按提交顺序回填（api.subscribe 返回里不一定带 examId）
              accepted.forEach((node, idx) => {
                if (results[idx]) {
                  map[node.id] = results[idx].remainTimes;
                  count++;
                } else {
                  log('batch', `登记失败：「${node.name}」id=${node.id}`);
                }
              });
              log('batch', `批量订阅完成：授权 ${accepted.length}，登记成功 ${count}`);
              this.setData({ subscribedMap: map }, () => this.buildSubList());
              wx.showToast({ title: `已订阅 ${count} 个节点`, icon: 'none' });
            });
            return;
          }
          const node = targets[i];
          const authOnce = () => {
            wx.requestSubscribeMessage({
              tmplIds: [node.templateId],
              success: (r) => {
                const ok = r[node.templateId] === 'accept';
                log('batch', `授权[${i + 1}/${targets.length}]「${node.name}」= ${r[node.templateId]}`);
                if (ok) accepted.push(node);
                step(i + 1);
              },
              fail: (err) => {
                // 预期内的授权链断开（非用户手势触发被拒），降级为 warn 避免控制台红字刷屏
                console.warn('[exam][batch] 授权链断开，弹继续授权:', node.name, err && err.errMsg);
                const msg = (err && err.errMsg) || '';
                // 授权链断开（常见于开发者工具：非用户手势调用被拒）。
                // 弹一个过渡弹窗，用户点「继续」的手势可以重新拉起授权。
                if (/TAP|gesture/i.test(msg) && i + 1 <= targets.length) {
                  log('batch', `手势链断开，等待用户点「继续」授权「${node.name}」`);
                  this._bmConfirmCb = () => authOnce();
                  this._bmCancelCb = () => {
                    log('batch', `用户跳过「${node.name}」`);
                    step(i + 1);
                  };
                  this.setData({
                    bmShow: true,
                    bmTitle: '继续授权',
                    bmIntro: `下一条是「${node.name}」（${node.examDate}），点「继续」弹出它的授权。`,
                    bmLines: [],
                    bmCancelText: '跳过',
                    bmConfirmText: '继续',
                  });
                  return;
                }
                step(i + 1);
              },
            });
          };
          authOnce();
        };
        step(0);
    };
    this.setData({
      bmShow: true,
      bmTitle: '批量订阅',
      bmIntro: `将依次授权以下 ${targets.length} 个节点的提醒，系统弹窗的弹出顺序与清单一致：`,
      bmLines: targets.map((n) => `${n.name}（${n.examDate}）`),
      bmCancelText: '取消',
      bmConfirmText: '开始订阅',
    });
  },

  /** 自定义批量订阅弹窗：点「开始订阅」 */
  onBmConfirm() {
    this.setData({ bmShow: false });
    const cb = this._bmConfirmCb;
    this._bmConfirmCb = null;
    if (cb) cb();
  },

  /** 自定义批量订阅弹窗：点「取消」或遮罩 */
  onBmCancel() {
    this.setData({ bmShow: false });
    this._bmConfirmCb = null;
    const cb = this._bmCancelCb;
    this._bmCancelCb = null;
    if (cb) cb();
  },

  /** 阻止弹窗卡片内的点击冒泡到遮罩（wxml catchtap 引用） */
  noop() {},

  /** 批量取消：把分组里已订阅的节点全部取消订阅（一次确认），未订阅的不动 */
  onBatchCancel(e) {
    const code = e.currentTarget.dataset.code;
    const group = (this.data.groups || []).find((g) => g.groupCode === code);
    if (!group) return;
    const targets = this._groupNodes(code).filter(
      (n) => !n.passed && this.data.subscribedMap[n.id] > 0
    );
    log('batchCancel', `点批量取消「${group.groupName}」：已订阅 ${targets.length} 个`);
    if (targets.length === 0) {
      log('batchCancel', '→ 该分组没有已订阅的节点');
      wx.showToast({ title: '该分组没有已订阅的节点', icon: 'none' });
      return;
    }
    wx.showModal({
      title: '批量取消订阅',
      content: `将取消「${group.groupName}」下 ${targets.length} 个已订阅节点的提醒，确定吗？`,
      confirmText: '全部取消',
      confirmColor: '#e64340',
      cancelText: '取消',
      success: (res) => {
        if (!res.confirm) return;
        wx.showLoading({ title: '取消中…', mask: true });
        Promise.all(
          targets.map((node) =>
            api.cancelSubscribe(node.id)
              .then((r) => (r && r.ok ? node.id : null))
              .catch(() => null)
          )
        ).then((ids) => {
          wx.hideLoading();
          const map = Object.assign({}, this.data.subscribedMap);
          let count = 0;
          ids.forEach((id) => {
            if (id) {
              delete map[id];
              count++;
            } else {
              log('batchCancel', '取消失败（接口未成功返回）');
            }
          });
          log('batchCancel', `批量取消完成：提交 ${targets.length}，成功 ${count}`);
          this.setData({ subscribedMap: map }, () => this.buildSubList());
          wx.showToast({ title: `已取消 ${count} 个节点`, icon: 'none' });
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
        log('load', `节点 ${(list || []).length} 个`, `自建 ${built.custom.length}`, `官方分组 ${built.official.length}`);
        this.setData({
          customNodes: built.custom,
          groups: built.official,
          loading: false,
        }, () => this.buildSubList());
      })
      .catch((err) => {
        console.error('[exam][load] fetchExams 失败:', err);
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
   * 分享卡片：挑一条「最值得晒」的节点。
   * 只挑官方节点——自建提醒是用户私事，且接收方列表里没有对应节点；
   * 优先已订阅的官方节点（用户最关心的），其次全部官方节点里日期最近的。
   * 分享落地带 examId，接收方打开会高亮该节点。
   */
  buildShare() {
    const cover = '/images/share-cover.jpg';
    const official = (list) => (list || []).filter((n) => !n.passed && !n.isCustom);
    const subs = official(this.data.subList);
    const all = official([].concat(...this.data.groups.map((g) => g.nodes)));
    const node = subs[0] || all[0] || null;
    if (!node) {
      return {
        title: '考试倒计时 · 重要考试日期，一个都不漏',
        path: '/pages/index/index?from=share',
        imageUrl: cover,
      };
    }
    const when = node.days === 0 ? '就是今天' : node.days === 1 ? '只剩 1 天' : `还有 ${node.days} 天`;
    return {
      title: `${node.name}${when}，一起来盯倒计时`,
      path: `/pages/index/index?examId=${node.id}&from=share`,
      imageUrl: cover,
    };
  },

  onShareAppMessage() {
    const info = this.buildShare();
    log('share', `分享卡片：${info.title}`);
    return info;
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
    const remain = this.data.subscribedMap[id] || 0;
    log('tap', `节点「${name}」`, `id=${id}`, `自建=${!!iscustom}`, `剩余=${remain}`,
      `模板=${templateId ? '有' : '无'}`, `已结束=${!!(node && node.passed)}`);
    if (node && node.passed) {
      log('tap', '→ 已结束，提示无法订阅');
      wx.showToast({ title: '该节点已结束，无法订阅', icon: 'none' });
      return;
    }
    if (!templateId) {
      log('tap', '→ 缺少模板ID，中断');
      wx.showToast({ title: '缺少模板ID，请联系管理员', icon: 'none' });
      return;
    }
    // 与 pill「已订阅」的渲染口径保持一致（truthy 判断），避免数值/字符串类型
    // 差异导致按钮显示已订阅、点击却走了重新授权分支
    if (this.data.subscribedMap[id]) {
      log('tap', '→ 已订阅，打开操作菜单');
      this.openActions(id, name, iscustom, templateId);
      return;
    }
    log('tap', '→ 未订阅，直接拉起授权');
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
    log('menu', `打开操作菜单「${name}」`, `剩余=${remain}`, `未来时点=${futureDays.length}`,
      `可再订=${canResub}`, `自建=${!!iscustom}`, `菜单=${[canResub ? '再订' : '', iscustom ? '删除提醒' : '取消订阅'].filter(Boolean).join('/')}`);
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
              log('cancel', `取消订阅成功「${name}」id=${id} 服务端删除记录数=${r.removed}`);
              this.setData({ subscribedMap: map }, () => this.buildSubList());
              wx.showToast({ title: '已取消订阅', icon: 'success' });
            } else {
              log('cancel', `取消订阅被拒「${name}」id=${id} msg=${(r && r.msg) || ''}`);
              wx.showToast({ title: (r && r.msg) || '取消失败，请重试', icon: 'none' });
            }
          })
          .catch((err) => {
            console.error('[exam][cancel] 取消订阅接口异常:', id, err);
            wx.showToast({ title: '取消失败，请重试', icon: 'none' });
          });
      },
    });
  },

  /**
   * 「再订 1 条提醒」入口：先判断再订还有没有意义。
   * 提醒点按节点类型固定（统一为当天/前1天/前2天），剩余额度已覆盖全部未来
   * 提醒点时，再订拿到的授权永远消费不掉，只会让「剩余次数」虚高 —— 直接说明，不拉授权。
   */
  onResubscribeTap(id, name, templateId) {
    const node = (this._allNodes || []).find((n) => n.id === id);
    const remain = this.data.subscribedMap[id] || 0;
    const futureDays = node ? futurePushDays(node.nodeType, node.examDate) : [];
    if (futureDays.length === 0) {
      // 没有未来推送时点了（提醒点全部过去），再订的授权永远消费不掉
      log('resub', `「${name}」无未来提醒点，不拉起授权`);
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
      log('resub', `「${name}」剩余 ${remain} 已覆盖未来 ${futureDays.length} 个时点，不拉起授权`);
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
   * 用户看到系统授权弹窗时常常不知道自己在授权什么，这里先讲清楚再走授权，
   * 并告诉他这条授权对应的具体推送时间（第 remain+1 个未来提醒点）。
   */
  confirmResubscribe(id, name, templateId) {
    const node = (this._allNodes || []).find((n) => n.id === id);
    const remain = this.data.subscribedMap[id] || 0;
    const futureDays = node ? futurePushDays(node.nodeType, node.examDate) : [];
    let time = '';
    if (node && futureDays.length > remain) {
      // 推送优先保证靠近截止日：新增这条授权把覆盖范围向截止日前多推一天
      const d = futureDays[futureDays.length - 1 - remain];
      time = `${fmtDate(parseDate(node.examDate) - d * DAY_MS)} ${PUSH_TIME}`;
    }
    wx.showModal({
      title: '再订 1 条提醒',
      content: time
        ? `同意在 ${time} 增加「${name}」的提醒`
        : `同意增加「${name}」的提醒`,
      confirmText: '同意',
      cancelText: '取消',
      success: (res) => {
        if (res.confirm) {
          this.doSubscribe(id, templateId);
        }
      },
    });
  },

  doSubscribe(id, templateId) {
    // 防浪费兜底：节点已订阅且剩余次数已覆盖全部未来推送时点时，再授权也
    // 不会多发推送，不拉起微信授权，直接转操作菜单（内部用节点名/类型，避免
    // 依赖调用方传参不准）
    const node = (this._allNodes || []).find((n) => n.id === id);
    const remain = Number(this.data.subscribedMap[id]) || 0;
    if (node && remain > 0) {
      const futureDays = futurePushDays(node.nodeType, node.examDate);
      if (futureDays.length > 0 && remain >= futureDays.length) {
        log('sub', `「${node.name}」剩余 ${remain} 已覆盖未来 ${futureDays.length} 个时点，不拉授权，转操作菜单`);
        this.openActions(id, node.name, !!node.isCustom, templateId);
        return;
      }
    }
    log('sub', `拉起授权「${node ? node.name : id}」templateId=${templateId}`);
    wx.requestSubscribeMessage({
      tmplIds: [templateId],
      success: (res) => {
        const result = res[templateId];
        log('sub', `授权结果「${node ? node.name : id}」= ${result}`);
        if (result !== 'accept') {
          // 用户在授权弹窗点了「取消」：静默忽略，不弹提示
          return;
        }
        api.subscribe(id)
          .then((r) => {
            if (r && r.ok) {
              const map = Object.assign({}, this.data.subscribedMap);
              map[id] = r.remainTimes;
              log('sub', `订阅登记成功「${node ? node.name : id}」remainTimes=${r.remainTimes}`);
              // 成功后不弹 toast：消息清单和按钮状态会立刻更新，足够反馈了
              this.setData({ subscribedMap: map }, () => this.buildSubList());
            } else {
              log('sub', `订阅登记被拒「${node ? node.name : id}」msg=${(r && r.msg) || ''}`);
              wx.showToast({ title: (r && r.msg) || '订阅失败', icon: 'none' });
            }
          })
          .catch((err) => {
            console.error('[exam][sub] 订阅接口异常:', id, err);
            wx.showToast({ title: '订阅失败，请重试', icon: 'none' });
          });
      },
      fail: (err) => {
        console.error('[exam][sub] 授权调用失败:', id, err);
        wx.showToast({ title: '订阅授权失败', icon: 'none' });
      },
    });
  },
});
