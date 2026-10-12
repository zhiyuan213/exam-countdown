const api = require('./utils/api');

App({
  globalData: {
    openid: '',
    exams: [],
  },

  onLaunch() {
    // 朋友圈分享打开 = 单页模式（scene 1154）：wx.login / 云托管调用均不可用，
    // 记录状态并跳过登录，避免无意义的报错；页面侧据此做静态渲染。
    const lo = (wx.getLaunchOptionsSync && wx.getLaunchOptionsSync()) || {};
    this.globalData.scene = lo.scene;
    this.globalData.singlePage = lo.scene === 1154;
    if (this.globalData.singlePage) {
      console.log('[exam] 单页模式（朋友圈分享打开），跳过登录');
      return;
    }
    if (!wx.cloud) {
      console.error('当前基础库不支持云能力，请使用 2.2.3 以上版本');
      return;
    }
    // 登录（openid 由云托管注入，无需 code）。失败不阻塞首页渲染。
    api.login()
      .then((res) => {
        this.globalData.openid = res.openid;
        console.log('login ok, openid =', res.openid);
      })
      .catch((err) => {
        console.error('login failed:', err);
      });
  },
});
