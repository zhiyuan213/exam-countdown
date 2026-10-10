# exam-countdown · 考试节点倒计时小程序（全量恢复）

> 旧电脑已格式化、源码全丢。本项目是**从微信云托管线上容器中逆向恢复**的完整工程。
> 后端 20 个类由线上 jar 反编译还原并重建为可编译工程；小程序前端因不在 jar 内，按接口契约重写。

## 恢复成果一览

| 部分 | 状态 | 位置 |
|---|---|---|
| 后端 jar | ✅ **字节级取回**（MD5 与线上完全一致） | 本地保留，见 `.gitignore` 说明 |
| 后端源码 | ✅ **20 个类全部还原**，编译/启动验证通过 | `backend/` |
| 数据库 | ✅ 4 张表结构 + 线上真实数据 | `db/restore.sql` |
| 小程序前端 | ⚠️ 重写（原前端不在 jar 内，无法恢复） | `miniprogram/` |
| 接口文档 | ✅ 完整契约 + 错误码表 | `docs/API.md` |
| 恢复档案 | ✅ 全过程记录与证据 | `RECOVERY.md` |

## 关键证据：jar 取回闭环

线上容器内 `app.jar` 被平台限制为单文件 10240 字节的下载接口，只能切片搬回（3159 片 × 9216 字节）：

```
字节数   29110371  =  29110371     ✅
分片数       3159  =       3159     ✅
MD5  c9bf7717958a0cb8e89d022689833576
   = c9bf7717958a0cb8e89d022689833576  ✅ 逐字节相同
```

## 目录说明

```
exam-countdown/
├── backend/            # 重建的后端 Maven 工程（可直接 mvn package）
├── miniprogram/        # 重写的小程序前端（微信开发者工具直接导入）
├── db/
│   ├── restore.sql     # 一次性重建库（DDL + 线上真实数据）
│   ├── schema.sql      # 建表语句
│   └── raw/            # 腾讯云 DMC 原始导出（4 份）
├── docs/API.md         # 接口契约
├── src-decompiled/     # Vineflower 反编译原始输出（20 个 .java）
├── src-extracted/      # jar 内 classes + MANIFEST + 依赖清单
├── recover/            # 分片取回脚本（jar/分片/工具默认不入库）
│   ├── recover_all.py  # 二分定位末片 + 批量下载 + 拼装 + ZIP 自校验
│   └── fetch_parts.py  # 同类下载器（简化版）
├── .gitignore
└── RECOVERY.md         # 恢复档案
```

## 快速开始

```bash
# 1. 建库（结构和线上数据一起还原）
mysql -u root -p < db/restore.sql

# 2. 编译后端
cd backend && mvn clean package -DskipTests

# 3. 配置并运行
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/exam_countdown?..."
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=<密码>
export WX_APPID=<appid>
export WX_SECRET=<secret>
export WX_TEMPLATE_ID=<模板ID>
export WX_CLOUD_CALL=false      # 本地走公网 token 通道
java -Xmx400m -jar target/app.jar

# 4. 前端：微信开发者工具导入 miniprogram/ 目录
#    记得先把 utils/api.js 里的 ENV_ID 占位符换成你自己的云托管环境 ID
```

## 🔒 入库前的脱敏处理

本仓库为**公开存档**，以下内容已做脱敏，不影响工程完整性：

| 项目 | 处理方式 |
|---|---|
| `WX_SECRET`（AppSecret） | 原 jar 内 `application.yml` 的默认值已清空为 `${WX_SECRET:}` |
| `SPRING_DATASOURCE_PASSWORD` | 同上，清空默认值 |
| 云托管集群 / 命名空间 / 环境 ID | 从档案与脚本中移除，改由环境变量传入 |
| 线上原始 `app.jar` | 内嵌配置含真实密钥，默认不纳入版本库（见 `.gitignore`） |
| `ENV_ID`（前端） | 已填入云托管环境 ID `prod-exam-d9g96663daa04cb57`（此前为占位符） |

> 小程序 **AppID** 与 **订阅消息模板 ID** 属客户端可见信息，未脱敏。

### 取回脚本的环境变量

`recover/recover_all.py` 与 `fetch_parts.py` 需要以下变量才能运行：

```bash
export WB_WS_HOST=sh-xxxx-webconsole.cloud.tencent.com   # WebShell 域名（地址栏可见）
export WB_CLUSTER_ID=cls-xxxxxxxx
export WB_POD_ID=<pod 名>
export WB_CONTAINER_NAME=<容器名>
export WB_NAMESPACE=<命名空间>
```

## ⚠️ 安全待办（重要）

线上运行日志里曾明文暴露下列凭据，务必全部轮换：

1. 小程序 **AppSecret** → 微信公众平台重置
2. **数据库密码** → 云托管 MySQL 改密，同步更新环境变量
3. **`REMIND_JOB_TOKEN`** → 换成新的随机串
4. 腾讯云控制台会话（含 `skey`）曾用于取回文件 → 退出登录使其失效

重建后的 `backend/src/main/resources/application.yml` 已把默认值全部留空为占位符，不会再随代码泄露。

## 数据说明

`db/raw/*.sql` 与 `db/restore.sql` 中的记录为**开发期自测数据**（2 个用户、3 条订阅、104 条推送日志、12 条考试节点）。
其中 `wx_user` / `subscription` / `push_log` 含真实 `openid`，若仓库公开且你介意，可将其替换为占位串。

## 额度包购买（虚拟支付 · 二期）

用户可以购买「提醒额度包」提升**可同时管理的提醒数上限**（`user_quota.paid_quota`）。
走微信官方虚拟支付的道具直购模式（`short_series_goods`），全终端覆盖，含 iOS。

### 开通后的配置清单

云托管控制台 → 服务 → 环境变量：

```
XPAY_ENABLED=true
XPAY_OFFER_ID=<mp 后台「虚拟支付 → 基础配置」的 OfferID>
XPAY_APP_KEY=<现网 AppKey>            # 密钥，不要提交仓库
XPAY_APP_KEY_SANDBOX=<沙箱 AppKey>     # 调试用
XPAY_ENV=0                            # 0 现网 / 1 沙箱
XPAY_PUSH_TOKEN=<mp 后台「消息推送」的 Token>
XPAY_GOODS=quota10:600:10:+10 个提醒额度;quota30:1200:30:+30 个提醒额度
```

mp 后台还要做三件事：

1. 「虚拟支付 → 道具管理」按 `XPAY_GOODS` 里的 goodsId / 价格逐个创建道具并发布（价格单位是元，代码里是分）
2. 「虚拟支付 → 基本配置 → 发货推送配置」填 `https://<云托管公网域名>/api/pay/notify`
3. 云托管加一个定时任务，每 5~10 分钟 `POST /api/pay/recheck?token=<REMIND_JOB_TOKEN>`（兜底补发货）

表 `pay_order` 由 `schema.sql` 启动时自动创建，不用手动建表。

### 本地联调

- `XPAY_ENV=1` + 沙箱 AppKey 可跑沙箱（iOS 不支持沙箱支付，只能用安卓真机调）
- 前端 `USE_MOCK=true` 时购买流程完全走本地 mock：不拉起真实支付，点购买即视为支付成功并把额度加上去，用来验证 UI 与额度联动
- 接口契约见 `docs/API.md` 第 6 节
