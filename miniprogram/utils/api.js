/**
 * 云托管调用封装。
 *
 * 关键点：
 *  1. 走 wx.cloud.callContainer，微信会自动注入 X-WX-OPENID / X-WX-APPID 请求头，
 *     所以后端能直接拿到 openid，前端不需要自己换 code（登录接口因此可省 body）。
 *  2. header 里必须带 X-WX-SERVICE 指定云托管服务名。
 *  3. 云托管返回的对象包了一层 res.data，这里统一解包。
 */

// ⚠️ 改成你的云托管环境 ID（控制台 → 云托管 → 环境设置里能看到，形如 prod-xxxx-xxxxxxxx）
const ENV_ID = 'YOUR-CLOUDBASE-ENV-ID';

// 云托管服务名
const SERVICE = 'exam-backend';

// 当前小程序在矩阵里的标识：kaoyan / jiaozhi / main
// 不传或传 main 会返回全部考试，按需改
const APP_KEY = 'main';

// ★ 本地开发验证开关：true = 不请求后端，直接用 utils/mock-data.js 的数据
//   （数据来自 db/restore.sql 的线上真实 exam 表，app_key=main 且 status=1）
//   验证完改回 false 即恢复云托管调用，不影响任何线上逻辑。
const USE_MOCK = true;
const mockData = require('./mock-data');

/** mock 模式下模拟后端接口返回 */
function mockCall(path, method) {
  return new Promise((resolve) => {
    setTimeout(() => {
      if (path === '/api/exams' && method === 'GET') {
        resolve(mockData);
      } else if (path === '/api/subscribe' && method === 'POST') {
        resolve({ ok: true, remainTimes: 1 });
      } else if (path === '/api/auth/login') {
        resolve({ ok: true, mock: true });
      } else {
        resolve({});
      }
    }, 200); // 模拟网络延迟，能看到 loading 态
  });
}

let inited = false;

function ensureInit() {
  if (inited) return;
  wx.cloud.init({ env: ENV_ID, traceUser: true });
  inited = true;
}

function call(path, method = 'GET', data = null) {
  if (USE_MOCK) return mockCall(path, method);
  ensureInit();
  return new Promise((resolve, reject) => {
    wx.cloud.callContainer({
      config: { env: ENV_ID },
      path,
      method,
      header: {
        'X-WX-SERVICE': SERVICE,
        'X-EXAM-APP': APP_KEY,
        'content-type': 'application/json',
      },
      data: data || undefined,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data);
        } else {
          reject(new Error(`HTTP ${res.statusCode}: ${JSON.stringify(res.data)}`));
        }
      },
      fail: (err) => reject(err),
    });
  });
}

/** 登录：openid 由云托管注入，无需传 code */
function login() {
  return call('/api/auth/login', 'POST', {});
}

/** 上架节点列表（已按 exam_date 升序） */
function fetchExams() {
  return call('/api/exams', 'GET');
}

/** 订阅提醒，返回 { ok, remainTimes } */
function subscribe(examId) {
  return call('/api/subscribe', 'POST', { examId });
}

module.exports = { call, login, fetchExams, subscribe, ENV_ID, SERVICE, APP_KEY };
