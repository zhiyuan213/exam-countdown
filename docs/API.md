# 考试倒计时 · 后端接口契约

来源：从线上 `app.jar`（023 版本）反编译还原，字段名/路径/行为与原实现一致。

## 约定

- **基址**：云托管内网调用走 `wx.cloud.callContainer`（`path` 里不带 `/api` 前缀时需要自行拼接，见下）
- **身份**：云托管会自动注入请求头，后端优先读它们，**前端不需要传 openid**
  - `X-WX-OPENID` / `X-WX-FROM-OPENID` → 当前用户 openid
  - `X-WX-APPID` / `X-WX-FROM-APPID` → 来源小程序 AppID
- **矩阵标识**：`X-EXAM-APP`（可选，`kaoyan` / `jiaozhi`；不传或传 `main`/`all` 返回全部）

---

## 1. 上架节点列表

```
GET /api/exams
Header: X-EXAM-APP: <可选>
```

返回 `status=1` 的节点，按 `exam_date` 升序：

```json
[
  {
    "id": 1,
    "name": "考研初试",
    "examDate": "2026-12-19",
    "signupDate": "2026-10-08",
    "ticketDate": "2026-12-10",
    "status": 1,
    "appKey": "main",
    "appid": "wx23a7083f915b62a1",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "groupCode": "kaoyan",
    "groupName": "考研",
    "nodeType": "EXAM",
    "sort": 40
  }
]
```

> 前端按 `groupCode` 分组、按 `sort` 排序展示即可（同一场考试 = 一串节点）。

## 2. 登录

```
POST /api/auth/login
Body(可选): { "code": "<wx.login 拿到的 code>" }
```

- 云托管调用 → openid 从请求头取，`code` 可省。
- 非云托管（本地调试）→ 传 `code`，后端走 `jscode2session`。

返回：

```json
{ "openid": "oXXXXXXXXXXXXXXXXXXX" }
```

副作用：openid 不在 `wx_user` 中则自动插入。

失败会抛 `RuntimeException("缺少身份请求头...")` → 500。

## 3. 订阅提醒

```
POST /api/subscribe
Body: { "examId": 1, "openid": "<可选，header 取不到时的兜底>" }
```

返回：

```json
{ "ok": true, "remainTimes": 2 }
```

- 每次调用 **`remain_times` +1**（一次授权 = 一次推送额度）。
- `template_id` 一律取 `exam.template_id`，为空才回落全局默认。
- 考试不存在 → `{ "ok": false, "msg": "考试不存在" }`
- openid 缺失 → `{ "ok": false, "msg": "openid 缺失，请重新进入小程序" }`

## 4. 手动触发提醒任务

```
GET /api/job/remind?token=<REMIND_JOB_TOKEN>
```

- token 与配置项 `remind.job-token` 全等才执行，否则 `{ "ok": false, "msg": "token 无效" }`
- 执行成功 → `{ "ok": true }`

---

## 定时推送逻辑（RemindJob）

- **触发**：`@Scheduled(cron = "${remind.cron}")`，默认 `0 0 8 * * ?`（每天 08:00，`Asia/Shanghai`）；也可由 `/api/job/remind` 外部拉起
- **命中条件**：`status=1` 且 `DATEDIFF(exam_date, today)` ∈ `remind.nodes`（默认 `30,7,1,0`）
- **推送对象**：`subscription` 中 `exam_id = 该节点` 且 `remain_times > 0` 的全部 openid
- **模板数据字段**（⚠️ 必须与微信后台模板字段完全对应，否则 `47003`）：

| key | 值 |
|---|---|
| `thing1` | 节点名称（截断 20 字） |
| `time2` | 节点日期 `yyyy年M月d日 09:00` |
| `number3` | 剩余天数 |
| `thing4` | 按 `node_type` 生成的文案（截断 20 字） |

- **跳转页**：`pages/index/index?examId=<id>`
- **额度处理**：
  - `errcode == 0` → `remain_times - 1`，日志 `OK`
  - `errcode == 43101`（用户拒收）→ `remain_times = 0`，日志 `FAIL / 用户拒收 43101`
  - 其他 → 日志 `FAIL / errcode=N`
- 每次尝试都写 `push_log`（成功失败都写）

---

## 微信侧错误码（线上曾出现）

| errcode | 含义 | 线上原因 |
|---|---|---|
| `47003` | 模板参数不合法 | 上表 4 个 key 与模板字段不匹配 |
| `40003` | openid 无效 | 用错 AppID 发（矩阵化场景） |
| `43101` | 用户拒收 | 正常，置额度为 0 |
| `40001` / `42001` | access_token 失效 | 已实现自动 `refresh()` 重试一次 |
| `-1` | 微信侧系统繁忙 | 早期偶发 |
