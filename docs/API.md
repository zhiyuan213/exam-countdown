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

---

## 5. 我的提醒（用户自建提醒）· 2026-10 新增

### ID 命名空间（重要）

官方节点与自建节点共用 `subscription.exam_id`，靠数值区间区分：

| 区间 | 含义 |
|---|---|
| `< 1000000000` | 官方节点，值 = `exam.id` |
| `>= 1000000000` | 自建节点，值 = `1000000000 + user_exam.id`（见 `UserExam.ID_BASE`） |

前端拿到 `/api/exams` 里的 `id` 后原样透传给 `/api/subscribe`、`DELETE /api/exams/custom/{id}` 即可，不需要自己换算。

### 额度规则

- `limit = user_quota.free_quota(默认 3) + user_quota.paid_quota`
- `used` = 该用户 `user_exam` 中 `status=1` 且 `exam_date >= 今天` 的条数
- 删除提醒或提醒过期 → 自动释放，不做扣减累计（避免退款纠纷）
- 二期接入虚拟支付后，支付回调调用 `QuotaService.addPaidQuota(openid, delta)`

### 接口

**我的提醒列表 + 额度**

```
GET /api/exams/custom
→ { "ok": true, "quota": {...}, "items": [ {id, name, examDate, templateId, nodeType:"CUSTOM", groupCode:"custom", groupName:"我的提醒", sort:0, isCustom:true, daysLeft} ] }
```

**新建提醒**

```
POST /api/exams/custom
Body: { "name": "雅思口语考试", "examDate": "2026-12-01" }
→ 成功 { "ok": true, "item": {...}, "quota": {...} }
→ 额度不足 { "ok": false, "code": "QUOTA_EXCEEDED", "msg": "...", "quota": {...} }
```

校验：`name` 非空且 ≤20 字（订阅消息 `thing` 字段限制）、`examDate` 不早于今天。建库时把全局默认模板 ID 写入 `user_exam.template_id`，前端直接拿它去 `wx.requestSubscribeMessage`。

**删除提醒**

```
DELETE /api/exams/custom/{id}
→ { "ok": true, "quota": {...} }
```

逻辑删除（`status=0`），同时清理该节点的 `subscription` 记录。

**查询额度**

```
GET /api/quota
→ { "ok": true, "freeQuota": 3, "paidQuota": 0, "limit": 3, "used": 1, "remaining": 2 }
```

### `/api/exams` 的变化

带 openid（云托管注入）请求时，返回值会**追加**该用户的自建节点（`isCustom: true`，分组 `我的提醒`）；官方节点字段与历史版本完全一致，仅多一个 `isCustom: false`。前端按 `groupCode` 分组渲染，自建节点排在最后。

### 自建提醒的推送

- 命中节点：`remind.custom-nodes`，默认 `1,0`（**考前 1 天 + 当天**）
  - 刻意不给自建提醒配 30/7 天：一次性订阅每推一条都要用户当场授权，提醒点太多会劝退用户
  - 想改直接在云托管环境变量里配 `REMIND_CUSTOM_NODES=7,1,0`
- `node_type` 固定为 `CUSTOM`，`thing4` 文案：`距你设置的日子还有 N 天，记得再订阅` / `今天就是你设的提醒日，记得再订阅`
- 跳转页同样带 `examId`，用命名空间 id 定位并高亮
- 额度扣减、`43101` 处理与官方节点一致

