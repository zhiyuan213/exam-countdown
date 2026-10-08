const api = require('./utils/api');

App({
  globalData: {
    openid: '',
    exams: [],
  },

  onLaunch() {
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
