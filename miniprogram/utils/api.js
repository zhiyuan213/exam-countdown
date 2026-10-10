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

// ---- mock 专用状态：自建提醒持久化到本地存储，重编译不丢，方便开发者工具里验证增删 ----
const MOCK_ID_BASE = 1000000000;   // 与后端 UserExam.ID_BASE 保持一致
const MOCK_FREE_QUOTA = 3;
const MOCK_STORE_KEY = 'mock_custom_list';
let mockCustom = [];
try {
  const saved = wx.getStorageSync(MOCK_STORE_KEY);
  if (Array.isArray(saved)) mockCustom = saved;
} catch (e) { /* 存储不可用就退化为纯内存 */ }
let mockCustomSeq = mockCustom.reduce((m, x) => Math.max(m, x.id - MOCK_ID_BASE), 0) + 1;

// mock 订阅状态：examId -> remainTimes，持久化到本地存储
const MOCK_SUB_KEY = 'mock_subscribed_map';
let mockSubscribed = {};
try {
  const savedSub = wx.getStorageSync(MOCK_SUB_KEY);
  if (savedSub && typeof savedSub === 'object') mockSubscribed = savedSub;
} catch (e) { /* ignore */ }

function mockPersist() {
  try {
    wx.setStorageSync(MOCK_STORE_KEY, mockCustom);
    wx.setStorageSync(MOCK_SUB_KEY, mockSubscribed);
  } catch (e) { /* ignore */ }
}

function mockReset() {
  mockCustom = [];
  mockCustomSeq = 1;
  mockSubscribed = {};
  mockPersist();
}

function todayStr() {
  const d = new Date();
  const p = (n) => (n < 10 ? '0' + n : '' + n);
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate());
}

function mockQuota() {
  const used = mockCustom.filter((x) => x.examDate >= todayStr()).length;
  return {
    ok: true,
    freeQuota: MOCK_FREE_QUOTA,
    paidQuota: 0,
    limit: MOCK_FREE_QUOTA,
    used,
    remaining: Math.max(MOCK_FREE_QUOTA - used, 0),
  };
}

/** mock 模式下模拟后端接口返回 */
function mockCall(path, method, data) {
  return new Promise((resolve) => {
    setTimeout(() => {
      if (path === '/api/exams' && method === 'GET') {
        resolve(mockData.concat(mockCustom));
      } else if (path === '/api/quota' && method === 'GET') {
        resolve(mockQuota());
      } else if (path === '/api/exams/custom' && method === 'POST') {
        const body = data || {};
        const q = mockQuota();
        if (!body.name || !body.examDate) {
          resolve({ ok: false, msg: '请填写名称和日期' });
          return;
        }
        if (q.remaining <= 0) {
          resolve({
            ok: false,
            code: 'QUOTA_EXCEEDED',
            msg: '提醒额度已用完（' + q.used + '/' + q.limit + '），删除旧提醒后再添加',
            quota: q,
          });
          return;
        }
        const item = {
          id: MOCK_ID_BASE + mockCustomSeq++,
          name: body.name,
          examDate: body.examDate,
          templateId: (mockData[0] && mockData[0].templateId) || '',
          nodeType: 'CUSTOM',
          groupCode: 'custom',
          groupName: '设置提醒',
          sort: 0,
          isCustom: true,
        };
        mockCustom.push(item);
        mockPersist();
        console.log('[mock] custom created:', item, 'quota:', mockQuota());
        resolve({ ok: true, item, quota: mockQuota() });
      } else if (path.indexOf('/api/exams/custom/') === 0 && method === 'DELETE') {
        const id = Number(path.split('/').pop());
        mockCustom = mockCustom.filter((x) => x.id !== id);
        mockPersist();
        console.log('[mock] custom deleted:', id, 'quota:', mockQuota());
        resolve({ ok: true, quota: mockQuota() });
      } else if (path === '/api/subscribe' && method === 'POST') {
        const examId = Number((data || {}).examId);
        if (examId) {
          mockSubscribed[examId] = (mockSubscribed[examId] || 0) + 1;
          mockPersist();
        }
        console.log('[mock] subscribed:', examId, 'remainTimes:', mockSubscribed[examId]);
        resolve({ ok: true, remainTimes: mockSubscribed[examId] || 1 });
      } else if (path === '/api/subscribe/cancel' && method === 'POST') {
        const examId = Number((data || {}).examId);
        const existed = !!mockSubscribed[examId];
        delete mockSubscribed[examId];
        mockPersist();
        console.log('[mock] subscription cancelled:', examId);
        resolve({ ok: true, removed: existed ? 1 : 0 });
      } else if (path === '/api/subscribe/list' && method === 'GET') {
        const items = Object.keys(mockSubscribed)
          .filter((k) => mockSubscribed[k] > 0)
          .map((k) => ({ examId: Number(k), remainTimes: mockSubscribed[k] }));
        resolve({ ok: true, items });
      } else if (path === '/api/auth/login') {
        resolve({ ok: true, mock: true, openid: 'mock-openid' });
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
  if (USE_MOCK) return mockCall(path, method, data);
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

/** 订阅提醒，返回 { ok, remainTimes }。自建节点的 examId 走命名空间 id，同样透传即可 */
function subscribe(examId) {
  return call('/api/subscribe', 'POST', { examId });
}

/** 当前用户已订阅的节点列表，返回 { ok, items: [{examId, remainTimes}] } */
function fetchSubscriptions() {
  return call('/api/subscribe/list', 'GET');
}

/** 取消订阅（删除该节点的推送额度），返回 { ok, removed } */
function cancelSubscribe(examId) {
  return call('/api/subscribe/cancel', 'POST', { examId });
}

/** 设置提醒额度，返回 { ok, freeQuota, paidQuota, limit, used, remaining } */
function fetchQuota() {
  return call('/api/quota', 'GET');
}

/** 新建设置提醒，返回 { ok, item, quota } */
function createCustom(payload) {
  return call('/api/exams/custom', 'POST', payload);
}

/** 删除设置提醒，返回 { ok, quota }。id 用 /api/exams 返回的那个 id */
function deleteCustom(id) {
  return call('/api/exams/custom/' + id, 'DELETE');
}

module.exports = {
  call, login, fetchExams, subscribe, fetchSubscriptions, cancelSubscribe,
  fetchQuota, createCustom, deleteCustom, mockReset,
  IS_MOCK: USE_MOCK,
  ENV_ID, SERVICE, APP_KEY,
};
