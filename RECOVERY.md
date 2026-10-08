# 考试倒计时小程序 · 代码恢复档案

> 生成时间：2026-10-08
> 背景：旧电脑已格式化，源码丢失；本档案汇集从微信云托管线上容器中"考古"出的全部事实，用于重建工程。

---

## 1. 项目身份

| 项 | 内容 |
|---|---|
| 形态 | 微信小程序 + 微信云托管（后端） |
| 小程序 AppID | `wx23a7083f915b62a1` |
| 小程序状态 | 类目已创建、微信认证已认证、**备案审核中**（发布卡点） |
| 云托管环境 | `prod-exam`（环境 ID 已脱敏） |
| 集群 / 可用区 | 已脱敏 / `ap-shanghai-5` |
| 服务名 | `exam-backend` |
| 线上版本 | `exam-backend-023`，2026-09-30 10:16 发布，部署方式「本地上传」，实例数 1 |
| 历史版本 | 014 → 023（2026-09-29 ~ 09-30，一天迭代 10 版，备注含 "server config change"） |

---

## 2. 已确认的运行时事实

- 基础镜像**只有 JRE**：`/opt/java/openjdk/bin` 下仅 `java`、`jfr`、`jrunscript`、`keytool`、`rmiregistry` —— 无 `jar` / `unzip` / `python` / `git`。
- Java 版本：Temurin **17.0.20.1**（OpenJDK 64-Bit Server VM）。
- **启动命令**：`java -Xmx400m -jar app.jar`（工作目录 `/app`）。
- `/app` 内容：`app.jar`（28MB，2026-09-30 10:17）、`cert/`、`cert.log`。
- `cert/` 内的 `certificate.crt`、`privateKey.key`、`initcert.sh`、`initenv.sh` **属于云托管平台注入**（用于内网调用微信接口：自签证书 + `/etc/hosts` 指向 `169.254.10.1` + iptables DNAT 到 `127.0.0.1:29081/29444`），**不是业务代码，无需恢复**。
- 容器网络：国内域名可通（baidu 200），境外域名 DNS 解析失败 → 导出文件必须走国内通道。

---

## 3. 配置基线（来自容器环境变量）

| 变量 | 内容 |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://10.17.110.83:3306/exam_countdown?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true`（内网地址，仅容器内可达） |
| `SPRING_DATASOURCE_USERNAME` | `root` |
| `SPRING_DATASOURCE_PASSWORD` | 已从本文档移除（曾明文出现在会话中，建议轮换） |
| `WX_APPID` | `wx23a7083f915b62a1` |
| `WX_SECRET` | 已移除（曾明文出现，建议在小程序后台重置 AppSecret） |
| `WX_TEMPLATE_ID` | 订阅消息模板 ID（值见云托管环境变量） |
| `REMIND_JOB_TOKEN` | 定时提醒任务的鉴权令牌（建议轮换） |
| `TZ` | `Asia/Shanghai` |

> 结论：后端通过环境变量注入配置（12-factor 风格），`application.yml` 里大概率只有占位符 —— 重建时沿用同一套变量名即可无痛对接。

---

## 4. 功能模型（已由线上表结构确认）

依据：库名 `exam_countdown` + 用户导出的 4 张表 + 订阅消息模板 + 定时提醒令牌。建表语句已存档至 `db/schema.sql`。

**产品形态：矩阵化「考试节点倒计时 + 订阅消息提醒」单后端多小程序**

| 表 | 作用 | 线上数据量 |
|---|---|---|
| `exam` | 考试及节点主数据 | 105 |
| `push_log` | 推送结果日志 | 104 |
| `subscription` | 订阅配额（一次授权=一次推送） | 3 |
| `wx_user` | 用户（openid） | 2 |

**核心链路**

1. 用户在某个小程序里选考试 → `wx.login` 取 openid → 写入 `wx_user`；
2. 每次点击「订阅提醒」→ 微信授权 → `subscription` 该 (openid, exam_id) 的 `remain_times` +1；
3. 后端定时任务（`REMIND_JOB_TOKEN` 鉴权）扫描即将到达的节点，向微信 `subscribeMessage.send` 推送，`remain_times` -1，结果写 `push_log`；
4. 推送节点：`D30` / `D7` / `D1` / `DAY`（考前 30 天 / 7 天 / 1 天 / 当天）。

**矩阵化设计（关键）**：同一套后端服务多个小程序，靠 `exam.app_key`（`kaoyan` / `jiaozhi`）、`exam.appid`、`exam.template_id` 三个字段区分租户——不同小程序共用考试数据但推送用各自的 AppID 与模板 ID。

**考试节点类型**：`SIGNUP` 报名 / `TICKET` 准考证打印 / `EXAM` 考试 / `SCORE` 成绩 / `RETEST` 复试。
一条 `exam` 记录同时带 `signup_date`、`ticket_date`、`exam_date`，即**一场考试 = 多个倒计时节点**。

**考试大类**：`group_code` + `group_name`（`kaoyan` 考研 / `jiaozhi` 教资 / `cet` 四六级 / `gongkao` 公考）。

**待用户确认**
1. 矩阵化目前实际开了几个小程序？（表里 `app_key` 至少出现 `kaoyan` / `jiaozhi`）
2. 前端页面结构：首页列表 / 考试详情 / 我的订阅 大概几屏？
3. 是否需要「管理端」（后台录入考试）还是纯手工写库？

---

## 4.1 线上数据快照与推送诊断（来自 MySQL 导出）

原始导出归档在 `db/raw/`（`exam.sql` / `push_log.sql` / `subscription.sql` / `wx_user.sql`）。

### exam —— 12 行，覆盖两个考试大类

| 组 | 节点行（id · node_type · sort） |
|---|---|
| 考研 `kaoyan` | 报名(11·SIGNUP·10) → 报名截止(12·SIGNUP·20) → 准考证打印(13·TICKET·30) → **初试(1·EXAM·40)** → 成绩公布(15·SCORE·50) → 国家线公布(16·SCORE·60) → 复试(17·RETEST·70) |
| 教资 `jiaozhi` | 面试报名(104·SIGNUP·10) → 面试准考证(105·TICKET·30) → **面试(101·EXAM·40)** → 2027 上半年笔试(102) → 2027 上半年面试(103) |

- 所有行 `app_key='main'`、`appid='wx23a7083f915b62a1'`、`template_id='CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws'` —— **矩阵化字段已建好，但线上实际只跑一个租户**。
- `status=0` 的三行（考研成绩公布 / 国家线公布 / 复试）是**未来节点，尚未上架**。
- **数据冗余点**：`考研初试` 行同时填了 `signup_date='2026-10-08'`、`ticket_date='2026-12-10'`，而这两个日期又各自有独立节点行（11 / 13）。说明"日期字段"与"节点行"两套表达并存，前端要么只用其一、要么重复展示 —— 重建时需确认以哪套为准。
- **异常线索**：`subscription` 中 exam 101 的 `template_id='yHgEQ0G-j_wYVI9D85PJa4WNursx3WsaD4Wgm_I8uE4'`，**与 exam 表中该考试的模板 ID 不一致** → 存在第二个小程序/模板（或模板被更换过），是 40003 失败的直接原因（见下）。

### push_log —— 79 行（id 25~104），暴露了定时任务与真实缺陷

- **推送任务频率约 1 分钟一次**：`created_at` 秒位基本是 `:00`，且常出现同一分钟内两条（如 `12:45:00` 与 `12:45:02`）→ 应为"主任务每分钟跑 + 失败后约 2 秒重试一次"。
- **每次尝试都落库**（失败也写）→ 这解释了"104 条日志 vs 3 条订阅"的错位，不是数据异常。
- 错误码分布：
  | errcode | 含义 | 出现场景 |
  |---|---|---|
  | `-1` | 微信侧系统繁忙 | 考研 exam 1 早期几次 |
  | `47003` | **模板参数不合法** | 考研 exam 1 大量出现（模板 data 的 key 与模板不匹配） |
  | `40003` | **openid 无效** | 教资 exam 101 连续出现 10 次 |
- **成功各 1 次**：`id=93`（2026-09-24 13:10:02，exam 1）与 `id=104`（2026-09-25 03:44:02，exam 101）—— 两个用户各自最终收到了 1 条 `D1` 提醒。
- **推论（重建时必须修正）**：`40003` 是 openid 与所用 AppID 不匹配的典型症状。矩阵化设计下，推送必须**按 `exam.appkey/appid` 取对应的 AppSecret 换取 access_token**，并**用 `exam.template_id` 而非客户端传来的 template_id**；`47003` 则说明订阅消息的 `data` key 没对齐模板字段。这两点都要在重建时按线上模板的实际字段修正。

### 其他表

- `subscription`：2 行，`remain_times` 均为 `0`（额度已消耗完）；两条 openid 前缀不同（`oz140x…` / `oHKYGx…`），进一步印证分属不同小程序。
- `wx_user`：2 行，与上面两个 openid 一一对应。
- **规模判断**：真实用户 2 人、订阅 2 条 —— 小程序尚未上线（备案仍在审核中），数据基本来自开发期自测。

---

## 5. 恢复进度

- [x] 定位线上容器、确认后端形态与技术栈
- [x] 找到可用的文件取回通道（WebShell 下载接口，10240 字节/文件，需会话 Cookie）
- [x] 拿到控制台会话 Cookie，鉴权通过；实测出容器内分片命名规则与总片数
- [x] **取出 `app.jar`：3159/3159 片全部命中（0 失败），拼装为 29,110,371 字节**
- [x] **MD5 校验通过：本地 `c9bf7717958a0cb8e89d022689833576` = 线上 `c9bf7717958a0cb8e89d022689833576`，字节级完全一致**
- [x] 导出 MySQL 表结构 + 数据（4 张表，已归档 `db/raw/`，合并为 `db/restore.sql`）
- [x] **反编译 `BOOT-INF/classes` 完成：20 个业务类全部还原为 Java 源码**
- [x] **重建为可编译 Maven 工程：`backend/`，编译 + 打包 + Spring 上下文启动全部通过**
- [x] 提取依赖清单（45 个 jar，版本精确锁定，与线上集合完全一致）
- [x] **小程序前端：已完成重写（`miniprogram/`）——jar 内不含前端，无法从后端恢复**
- [ ] 部署回云托管，数据接续、功能验证

### 5.0 取回校验（闭环证据）

| 校验项 | 线上（用户在容器内执行） | 本地（拼装结果） | 结果 |
|---|---|---|---|
| 字节数 `wc -c` | `29110371` | `29110371` | ✅ |
| 分片数 `ls /tmp/parts \| wc -l` | `3159` | `3159` | ✅ |
| MD5 | `c9bf7717958a0cb8e89d022689833576` | `c9bf7717958a0cb8e89d022689833576` | ✅ **逐字节相同** |

> 结论：本地 `recover/app.jar` 与线上 `exam-backend-023` 容器中的 `app.jar` **完全一致**，分片顺序还原无误，取回环节无任何信息损失。这是整个恢复工作中最强的正确性证明。

### 5.2 反编译与工程还原结果

| 项 | 结果 |
|---|---|
| 入口类 | `com.exam.countdown.ExamCountdownApplication` |
| 业务类 | 20 个（4 controller / 4 mapper / 4 entity / 3 dto / 2 service / 1 job / 1 config / 1 app） |
| 框架 | Spring Boot **3.3.4**，`@EnableScheduling` + `@MapperScan` |
| 持久层 | MyBatis-Plus **3.5.7**（`mybatis-plus-spring-boot3-starter`），全部 `BaseMapper`，无 XML |
| HTTP 客户端 | 仅 JDK 自带 `java.net.http.HttpClient`，无第三方 |
| 依赖 jar | 45 个，清单见 `src-extracted/DEPENDENCIES.txt` |
| 源码产物 | `src-decompiled/`（Vineflower 原生输出）、`backend/`（整理后的 Maven 工程） |

**重建验证（三层，全部通过）**

| 层次 | 方法 | 结果 |
|---|---|---|
| 编译 | `mvn clean package`（JDK 17.0.20.1 + Maven 3.9.16） | BUILD SUCCESS |
| 结构 | 比对重建 jar 与线上 jar 的条目 | class 集合 **20/20 完全相同**；依赖集合 **45/45 完全相同**；MANIFEST 一致 |
| 接口 | `javap -p` 逐类导出成员签名后比对 | **209 个成员声明，签名集合 100% 相同**（仅类内声明顺序不同） |
| 运行 | `java -jar` 启动 + Spring 上下文加载 | Tomcat 10.1.30 起在 8080；MyBatis-Plus 3.5.7 加载；`Started ExamCountdownApplication in 3.169s`，零报错 |

> 重建 jar 体积 29,112,426 字节，与线上 29,110,371 字节仅差 2,055 字节（0.007%）——差异来自 `application.yml` 中默认密钥已改为占位、以及新增了 `migration-v3.sql`。

**发现的接口**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/exams` | 上架节点列表（`X-EXAM-APP` 过滤，`main`/`all`/空 = 全部） |
| POST | `/api/auth/login` | 云托管请求头取 openid，兜底 `code2session` |
| POST | `/api/subscribe` | 订阅 +1 额度 |
| GET | `/api/job/remind` | token 鉴权后手动触发推送 |

**关键实现细节（原代码行为，重建时原样保留）**

1. **双通道发消息**：默认走**云调用**（`http://api.weixin.qq.com/...`，由云托管网关注入 token），异常则降级到公网 `access_token` 通道；后者遇 `40001/42001` 会自动 `refresh()` 重试一次。
2. **矩阵资源复用**：云调用时若 `exam.appid != WX_APPID`，在 URL 上追加 `?from_appid=<appid>`，即**以另一个小程序身份发送**。
3. **模板 ID 以后端为准**：`exam.template_id` 优先，为空才回落全局 `wx.template-id`。
4. **推送字段固定为** `thing1` 节点名 / `time2` 时间 / `number3` 剩余天数 / `thing4` 文案 —— 与后台模板字段不一致即报 **47003**（线上确有此报错）。
5. **额度规则**：成功 `-1`；`43101`（用户拒收）直接置 0；其他错误不动额度。每次尝试都写 `push_log`。
6. **定时**：`remind.cron` 默认 `0 0 8 * * ?`（Asia/Shanghai），节点 `remind.nodes` 默认 `30,7,1,0`。

### 5.1 分片命名规则（实测，非标准 split 命名）

容器内 `/tmp/parts/` 下共 **3159 片**，命名分两段：

| 序号区间 | 命名 | 示例 | 片数 |
|---|---|---|---|
| `idx` 0 ~ 649 | `p_` + base26(idx, 2) | `p_aa` … `p_yz` | 650 |
| `idx` 650 ~ 3158 | `p_` + `z` + base26(idx-650, 3) | `p_zaaa` … `p_zdsm` | 2509 |

- 单片 **9216 字节**（`split -b 9k`）；`p_za` 到 `p_zz` 的两位名**全部不存在**。
- 末片 `idx=3158 → p_zdsm`，由二分法实测（连续性抽样无空洞）。
- 推定 jar 大小 = 3159 × 9216 = **29,113,344 字节**（27.77 MiB，与 `ls -lh` 的 `28M` 一致）。
- 接口返回码含义：`200` 存在并返回分片；`404 + file or directory too big` 超限；`404 + download failed: du: ...` 文件不存在；`404 + 权限不足` Cookie 失效。

---

## 6. 文件取回通道（已实测）

**已确认可用：WebShell 下载接口**

```
https://<WEBCONSOLE_HOST>/download          # 形如 sh-xxxx-webconsole.cloud.tencent.com
  ?filePath=<URL编码的容器内路径>
  &clusterId=<CLUSTER_ID>                   # 形如 cls-xxxxxxxx
  &podId=<POD_NAME>
  &containerName=<CONTAINER_NAME>
  &namespace=<NAMESPACE>&tkeeks=1&shell=sh&tcb=1
```

> 上述标识均可从云托管控制台「云端调试」打开的 WebShell 地址栏中直接读到，
> 出于安全考虑本档案不保留真实值；批量下载脚本 `recover/recover_all.py`
> 通过环境变量 `WB_WS_HOST / WB_CLUSTER_ID / WB_POD_ID / WB_CONTAINER_NAME / WB_NAMESPACE` 传入。

- **限制：单文件上限 10240 字节**（超限报 `file or directory too big, max size is 10240 bytes`）。
- **需要控制台登录态**：无 Cookie 直连返回 `404 / 权限不足`（已实测）→ 必须携带浏览器会话 Cookie。
- 因此取回 28MB 的 `app.jar` 采用**分片方案**：容器内 `split -b 9k` 切片（约 3000 片），本地按序号批量下载后拼接还原。

**备选通道（未验证）**
1. Gitee 附件接口 / GitHub Release 附件 —— 取决于容器能否出网到对应域名（境外域名当前不可达）。
2. SFTP 推到自有服务器。
3. 腾讯云镜像仓库：控制台取镜像地址 → 本地 `docker pull` → 解包（可连同运行环境一并获得）。

---

## 7. 安全待办

- [ ] 小程序后台重置 **AppSecret**（`WX_SECRET` 曾明文暴露）
- [ ] 云托管环境中轮换 **数据库密码** 与 **`REMIND_JOB_TOKEN`**
- [ ] 轮换后同步更新服务配置里的环境变量并重新部署
- [ ] **控制台会话 Cookie 已明文出现在会话中**（含 `skey`）：取回工作完成后退出腾讯云控制台登录（或改密）使其失效；本地临时文件 `%TEMP%\wb_tc_cookie.txt` 用完即删
