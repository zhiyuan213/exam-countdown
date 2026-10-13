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
    "templateId": "qD0l3QHzRu-MOJl8A21mJxOzD-ykunkk-IjmGR-AJvw",
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

### 已订阅列表

```
GET /api/subscribe/list
```

返回当前用户 `remain_times > 0` 的订阅（前端据此把按钮渲染成「已订阅」）：

```json
{ "ok": true, "items": [ { "examId": 1, "remainTimes": 2 }, { "examId": 1000000001, "remainTimes": 1 } ] }
```

- `examId` 含自建节点（命名空间 id），与 `/api/exams` 返回的 id 同一体系。

### 取消订阅

```
POST /api/subscribe/cancel
Body: { "examId": 1 }
```

返回：`{ "ok": true, "removed": 1 }`

- 直接删除该用户在该节点上的订阅记录，之后不再推送。
- 前端交互：点已订阅的按钮 → 操作菜单 →「取消订阅」→ 二次确认。
- 已无订阅记录时同样返回 `ok=true`（`removed=0`），幂等。

## 4. 手动触发提醒任务

```
GET /api/job/remind?token=<REMIND_JOB_TOKEN>
```

- token 与配置项 `remind.job-token` 全等才执行，否则 `{ "ok": false, "msg": "token 无效" }`
- 执行成功 → `{ "ok": true }`

### 提醒点策略（重要，2026-10 调整）

微信规则是「一次授权只能推一条」，而任务每天跑一次、**命中最靠前的提醒点就扣掉一次额度**。
所以每个类型最该被提醒的时刻必须放在最前面，否则用户订 1 次只会收到最早的那条（例如考前 30 天），
真正的考试前反而收不到。

| 节点类型 | 提醒点（考前天数） | 订 1 次实际收到 |
|---|---|---|
| `EXAM` / `SCORE` / `RETEST` / `VOLUNTEER` / `ADMISSION` | `1,0` | 考前一天 |
| `SIGNUP`（报名类） | `3,1,0` | 提前 3 天 |
| `TICKET`（准考证类） | `7,1,0` | 提前 7 天 |
| 自建提醒（`custom-nodes`） | `1,0` | 提醒日的前一天 |
| 未知类型 | 回落 `remind.nodes`（默认 `30,7,1,0`） | 最早命中的提醒点 |

配置项都可被云托管环境变量覆盖，改提醒点不用改代码、不用重新构建：

- `REMIND_NODES_BY_TYPE`（默认 `EXAM:1,0;SCORE:1,0;RETEST:1,0;VOLUNTEER:1,0;ADMISSION:1,0;SIGNUP:3,1,0;TICKET:7,1,0`）
  格式 `类型:天数,天数;类型:天数`，类型大小写不敏感，可用 `DEFAULT:` 兜底
- `REMIND_NODES`（默认 `30,7,1,0`，仅当类型未命中时使用）
- `REMIND_CUSTOM_NODES`（默认 `1,0`）

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

## 5. 设置提醒（用户自建提醒）· 2026-10 新增

### ID 命名空间（重要）

官方节点与自建节点共用 `subscription.exam_id`，靠数值区间区分：

| 区间 | 含义 |
|---|---|
| `< 1000000000` | 官方节点，值 = `exam.id` |
| `>= 1000000000` | 自建节点，值 = `1000000000 + user_exam.id`（见 `UserExam.ID_BASE`） |

前端拿到 `/api/exams` 里的 `id` 后原样透传给 `/api/subscribe`、`DELETE /api/exams/custom/{id}` 即可，不需要自己换算。

### 额度规则

- `limit = user_quota.free_quota(默认 2，2026-10-11 由 3 下调) + user_quota.paid_quota`
- `used` = 该用户 `user_exam` 中 `status=1` 且 `exam_date >= 今天` 的条数
- 删除提醒或提醒过期 → 自动释放，不做扣减累计（避免退款纠纷）
- 二期接入虚拟支付后，支付回调调用 `QuotaService.addPaidQuota(openid, delta)`

### 接口

**设置提醒列表 + 额度**

```
GET /api/exams/custom
→ { "ok": true, "quota": {...}, "items": [ {id, name, examDate, templateId, nodeType:"CUSTOM", groupCode:"custom", groupName:"设置提醒", sort:0, isCustom:true, daysLeft} ] }
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
→ { "ok": true, "freeQuota": 2, "paidQuota": 0, "limit": 2, "used": 1, "remaining": 1 }
```

### `/api/exams` 的变化

带 openid（云托管注入）请求时，返回值会**追加**该用户的自建节点（`isCustom: true`，分组 `设置提醒`）；官方节点字段与历史版本完全一致，仅多一个 `isCustom: false`。前端按 `groupCode` 分组渲染，自建节点排在最后。

### 自建提醒的推送

- 命中节点：`remind.custom-nodes`，默认 `2,1,0`（**前 2 天 / 前 1 天 / 当天**，与官方节点统一节奏）
  - 刻意不给自建提醒配 30/7 天：一次性订阅每推一条都要用户当场授权，提醒点太多会劝退用户
  - 想改直接在云托管环境变量里配 `REMIND_CUSTOM_NODES=7,1,0`
- `node_type` 固定为 `CUSTOM`，`thing4` 文案：`距你设置的日子还有 N 天，记得再订阅` / `今天就是你设的提醒日，记得再订阅`
- 跳转页同样带 `examId`，用命名空间 id 定位并高亮
- 额度扣减、`43101` 处理与官方节点一致


---

## 6. 额度包购买（虚拟支付）· 2026-10 新增

买的是**同时可管理的提醒数上限**（`user_quota.paid_quota`），不是推送条数；删除或过期自动释放，所以不存在「用掉就没了」的争议。

模式选用官方**道具直购** `short_series_goods`（低频单次购买），全终端（含 iOS）走微信虚拟支付通道。

### 开通与配置（云托管环境变量）

| 变量 | 说明 |
|---|---|
| `XPAY_ENABLED` | 总开关。未配置时 `/api/pay/*` 返回「支付功能尚未开通」，不影响其他功能 |
| `XPAY_OFFER_ID` | mp 后台「虚拟支付 → 基础配置」里的 OfferID |
| `XPAY_APP_KEY` | 现网 AppKey（密钥，只配环境变量，**不要提交仓库**） |
| `XPAY_APP_KEY_SANDBOX` | 沙箱 AppKey（`XPAY_ENV=1` 时使用） |
| `XPAY_ENV` | `0` 现网 / `1` 沙箱 |
| `XPAY_PUSH_TOKEN` | mp 后台「消息推送」配置的 Token，用于校验发货推送来源 |
| `XPAY_GOODS` | `goodsId:priceFen:quota:name`，多项用 `;` 分隔 |

默认额度包：`quota10:600:6:永久增加6个提醒额度;quota30:1200:18:永久增加18个提醒额度`（¥6 / ¥12）。

⚠️ `goodsId` 必须与后台「道具管理」里的**道具 ID 完全一致**，`priceFen` 必须与后台价格**完全一致**，否则拉起支付会被拒。
⚠️ iOS 端可能存在最低金额门槛，若 iOS 报错先把单价调到 ≥ 6 元（环境变量与后台价格要同步改）。
⚠️ 道具需在后台「道具管理」逐个创建并发布（发布要审核），ID 创建后不可改。

### 商品列表

```
GET /api/pay/goods
→ { "ok": true, "enabled": true, "goods": [ { "goodsId": "quota10", "name": "永久增加6个提醒额度", "priceFen": 600, "quota": 6 } ] }
```

### 下单（返回拉起支付三件套）

```
POST /api/pay/order
body: { "goodsId": "quota10", "code": "<wx.login() 的 code>" }
→ { "ok": true, "orderId": "T20261010203000123456", "mode": "short_series_goods", "env": 0,
    "signData": "{...}", "paySig": "<64位hex>", "signature": "<64位hex>", "goods": {...} }
```

- `code` 是**必需**的：用户态签名 `signature = HMAC_SHA256(session_key, signData)`，后端要用 `code` 走 `jscode2session` 换 `session_key`
- `paySig = HMAC_SHA256(AppKey, "requestVirtualPayment&" + signData)`，`AppKey` 只存在于服务端
- `signData` 字段顺序固定为 `offerId / buyQuantity / env / currencyType / productId / goodsPrice / outTradeNo / attach`，**顺序即签名内容**，任何一处改动都会导致验签失败
- 下单即落库 `pay_order`（status=`INIT`），失败返回 `{ ok:false, code, msg }`，`code` 取值：`PAY_DISABLED` / `PAY_NOT_CONFIGURED` / `GOODS_NOT_FOUND` / `CODE_REQUIRED` / `SESSION_FAILED` / `SIGN_FAILED`

前端随后调用：

```js
wx.requestVirtualPayment({ signData, paySig, signature, mode: 'short_series_goods', ... })
```

### 查订单状态（支付后确认）

```
GET /api/pay/order/{orderId}
→ { "ok": true, "orderId": "...", "status": "DELIVERED", "quotaDelta": 10, "delivered": true }
```

`status`：`INIT` 未支付 / `PAID` 已支付待发货 / `DELIVERED` 已发货（额度已到账）。

**前端 `wx.requestVirtualPayment` 的 success 只算弱确认**，必须以本接口（后端状态）为准。发货由微信推送驱动、后端异步到账，所以前端轮询几次即可，没到账就提示「稍后到账」，不会丢单。

### 订单中心（当前用户订单列表）

```
GET /api/pay/my-orders
→ { "ok": true, "orders": [ { "outTradeNo": "...", "goodsName": "永久增加6个提醒额度",
      "productId": "quota10", "quotaDelta": 6, "priceFen": 600, "actualFen": 600,
      "status": "DELIVERED", "createdAt": "2026-10-11T17:30:00" } ] }
```

- openid 由云托管注入（`X-WX-OPENID`），无需登录；按下单时间倒序，最多 50 条
- 用途：小程序「订单中心」页 `pages/orders/orders`（微信要求「选购商品-下单-支付」类小程序提供订单中心页，提审需登记主 path）

### 发货推送（微信 → 本服务）

推送地址配置在 mp 后台「虚拟支付 → 基本配置 → 基础配置 → 发货推送配置」：
`https://<云托管公网域名>/api/pay/notify`

- `GET /api/pay/notify`：配置 URL 时微信的校验请求，原样回 `echostr`
- `POST /api/pay/notify`：事件 `xpay_goods_deliver_notify`，JSON / XML 两种格式都支持
  - 应答格式跟随请求格式：JSON → `{"ErrCode":0,"ErrMsg":"success"}`；XML → `<xml><Errcode>0</Errcode>...</xml>`
  - 应答不合法或返回失败，微信最多重推 15 次
- 幂等两道闸：① `pay_order.status` 已是 `DELIVERED` 直接返回成功；② 同一 `WeChatPayInfo.MchOrderNo` 已发过货也返回成功
- 发货动作：`QuotaService.addPaidQuota(openid, quotaDelta)`，累加 `user_quota.paid_quota`
- 找不到订单时返回失败（让微信重试），并打 `ERROR` 日志

### 兜底查单

```
POST /api/pay/recheck?token=<REMIND_JOB_TOKEN>
→ { "ok": true, "checked": 3, "delivered": 1 }
```

扫描近 6 小时仍为 `INIT` 的当前环境订单，调 `/xpay/query_order` 确认已支付则补发货。建议挂在云托管定时任务上（每 5~10 分钟一次），和提醒任务共用 `REMIND_JOB_TOKEN`。
