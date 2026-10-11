-- ============================================================
-- schema.sql v3：多考试/多节点版
-- 语义变更：exam 表一行 = 一个"考试节点"（考研初试/考研报名截止/教资面试...），
--           用 group_code 把同一场考试的一串节点聚合成一组，前端按组展示。
-- 旧字段 signup_date / ticket_date 保留只为兼容老数据，新数据一律用节点行表达。
-- ============================================================

CREATE TABLE IF NOT EXISTS exam (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  name        VARCHAR(64) NOT NULL COMMENT '节点名称（如：考研初试/考研报名截止），≤20字（订阅消息 thing 字段限制）',
  exam_date   DATE NOT NULL COMMENT '该节点的目标日期',
  signup_date DATE NULL COMMENT '旧字段，兼容保留',
  ticket_date DATE NULL COMMENT '旧字段，兼容保留',
  status      TINYINT DEFAULT 1 COMMENT '1上架 0下架',
  app_key     VARCHAR(32) NULL COMMENT '矩阵化：所属小程序标识 kaoyan/jiaozhi',
  appid       VARCHAR(32) NULL COMMENT '矩阵化：所属小程序 AppID',
  template_id VARCHAR(64) NULL COMMENT '矩阵化：该小程序的订阅消息模板ID（空则用全局默认）',
  group_code  VARCHAR(32) NULL COMMENT '多考试：考试大类标识 kaoyan/jiaozhi/cet/gongkao',
  group_name  VARCHAR(32) NULL COMMENT '多考试：大类显示名（考研/教资/四六级...）',
  node_type   VARCHAR(16) NULL COMMENT '节点类型 SIGNUP报名 TICKET准考证 EXAM考试 SCORE成绩/分数线 RETEST复试面试',
  sort        INT DEFAULT 0 COMMENT '同组内展示顺序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考试节点表';

CREATE TABLE IF NOT EXISTS wx_user (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  openid     VARCHAR(64) NOT NULL UNIQUE,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS subscription (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  openid       VARCHAR(64) NOT NULL,
  exam_id      BIGINT NOT NULL,
  template_id  VARCHAR(64) NOT NULL COMMENT '订阅消息模板ID',
  remain_times INT NOT NULL DEFAULT 1 COMMENT '剩余可推送次数',
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_exam (exam_id, remain_times),
  KEY idx_open (openid, exam_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅配额表（一次授权=1次推送额度）';

CREATE TABLE IF NOT EXISTS push_log (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  openid     VARCHAR(64) NOT NULL,
  exam_id    BIGINT NOT NULL,
  node_name  VARCHAR(16) COMMENT '提醒节点: D30/D7/D1/DAY',
  result     VARCHAR(16) COMMENT 'OK/FAIL',
  err_msg    VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推送日志';

-- ============================================================
-- 用户自建提醒（「设置提醒」）
-- 与官方节点表 exam 分开存：exam 是运营配置的官方节点，本表是用户自己加的节点。
-- ⚠️ 刻意不对 exam 表做 ALTER —— schema.sql 每次启动都会执行，
--    ALTER TABLE ADD COLUMN 在 MySQL 里不幂等，重复执行会让服务启动失败。
-- ID 命名空间约定（见 UserExam.ID_BASE）：
--    官方节点 id < 1000000000；自建节点对前端暴露的 id = 1000000000 + 本表 id。
--    subscription.exam_id 沿用同一套 id，从而一张订阅表同时覆盖两类节点。
-- ============================================================
CREATE TABLE IF NOT EXISTS user_exam (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  openid      VARCHAR(64) NOT NULL COMMENT '创建者 openid',
  name        VARCHAR(64) NOT NULL COMMENT '提醒名称，≤20字（订阅消息 thing 字段限制）',
  exam_date   DATE NOT NULL COMMENT '目标日期',
  template_id VARCHAR(64) NULL COMMENT '订阅消息模板ID（空则用全局默认）',
  appid       VARCHAR(32) NULL COMMENT '所属小程序 AppID（矩阵化预留）',
  status      TINYINT DEFAULT 1 COMMENT '1 有效 0 已删除',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_owner (openid, status),
  KEY idx_date (exam_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户自建提醒表';

-- ============================================================
-- 用户额度表。limit = free_quota + paid_quota 表示「可同时管理的未过期提醒数」。
-- free_quota 默认 2（免费额度，2026-10-11 由 3 下调为 2）；paid_quota 由支付回调累加。
-- 额度按「当前有效提醒数」实时计算，删除或过期即释放，不做扣减累计，避免退款纠纷。
-- ============================================================
CREATE TABLE IF NOT EXISTS user_quota (
  openid      VARCHAR(64) PRIMARY KEY COMMENT '用户 openid',
  free_quota  INT NOT NULL DEFAULT 2 COMMENT '免费额度（可同时管理的提醒数）',
  paid_quota  INT NOT NULL DEFAULT 0 COMMENT '已购额度（累加）',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户提醒额度表';

-- 额度配置对齐（幂等）：老库已有行 free_quota=3 统一降到 2。
-- 注意：若日后手工把某个用户调回 3，重启后会被这条重置，调整请改用 paid_quota。
UPDATE user_quota SET free_quota = 2 WHERE free_quota = 3;

-- ============================================================
-- 虚拟支付订单表（二期）。道具直购 short_series_goods 模式的订单流水。
-- 主键 outTradeNo 就是下单时下发给微信的业务订单号，发货推送靠它回捞订单。
-- 状态：INIT 已下单未支付 / PAID 已支付待发货 / DELIVERED 已发货（额度已到账）/ CLOSED 关闭
-- 幂等：状态为 DELIVERED 直接跳过；另用 wx_order_id（微信平台单号）做第二道去重。
-- ============================================================
CREATE TABLE IF NOT EXISTS pay_order (
  out_trade_no   VARCHAR(64)  NOT NULL COMMENT '业务订单号 T+时间戳+随机',
  openid         VARCHAR(64)  NOT NULL COMMENT '付款用户 openid',
  product_id     VARCHAR(64)  NOT NULL COMMENT '道具ID（与 mp 后台道具管理一致）',
  goods_name     VARCHAR(128) DEFAULT NULL COMMENT '额度包展示名',
  quota_delta    INT          NOT NULL DEFAULT 0 COMMENT '本单应发放额度数',
  quantity       INT          NOT NULL DEFAULT 1 COMMENT '购买数量',
  price_fen      INT          NOT NULL DEFAULT 0 COMMENT '道具单价（分）',
  actual_fen     INT          DEFAULT NULL COMMENT '实付金额（分，来自回调）',
  env            INT          NOT NULL DEFAULT 0 COMMENT '0 现网 1 沙箱',
  status         VARCHAR(16)  NOT NULL DEFAULT 'INIT' COMMENT 'INIT/PAID/DELIVERED/CLOSED',
  wx_order_id    VARCHAR(64)  DEFAULT NULL COMMENT '微信平台单号 WeChatPayInfo.MchOrderNo',
  transaction_id VARCHAR(64)  DEFAULT NULL COMMENT '微信支付交易号',
  attach         VARCHAR(255) DEFAULT NULL COMMENT '透传 openid|goodsId|quota',
  notify_count   INT          NOT NULL DEFAULT 0 COMMENT '收到发货推送次数',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paid_at        DATETIME     DEFAULT NULL,
  delivered_at   DATETIME     DEFAULT NULL,
  PRIMARY KEY (out_trade_no),
  KEY idx_openid_created (openid, created_at),
  KEY idx_status_created (status, created_at),
  KEY idx_wx_order (wx_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='虚拟支付订单表';

-- ============================================================
-- 虚拟支付退款记录表（二期）。
-- 收到 xpay_refund_notify 时落一条，用于审计留痕（退款单号/金额/时间）。
-- 为什么单独建表而不是给 pay_order 加列：schema.sql 每次启动都会执行，
-- ALTER TABLE ADD COLUMN 不幂等会让服务启动失败；新建表用 IF NOT EXISTS 则安全。
-- 与 pay_order 的关系：pay_order.status 置为 REFUNDED 表示「已退款并回收额度」，
-- 本表是明细流水（wx_refund_id 唯一，用于幂等去重）。
-- ============================================================
CREATE TABLE IF NOT EXISTS pay_refund (
  id            BIGINT PRIMARY KEY AUTO_INCREMENT,
  out_trade_no  VARCHAR(64)  NOT NULL COMMENT '业务订单号（对应 pay_order.out_trade_no）',
  openid        VARCHAR(64)  DEFAULT NULL COMMENT '退款用户 openid',
  wx_refund_id  VARCHAR(64)  DEFAULT NULL COMMENT '微信退款单号 WxRefundId',
  mch_refund_id VARCHAR(64)  DEFAULT NULL COMMENT '商户退款单号 MchRefundId',
  wx_order_id   VARCHAR(64)  DEFAULT NULL COMMENT '退款单对应的微信订单号 WxOrderId',
  refund_fee    INT          NOT NULL DEFAULT 0 COMMENT '退款金额（分）',
  ret_code      INT          DEFAULT NULL COMMENT '退款状态 0 成功 非 0 失败',
  ret_msg       VARCHAR(255) DEFAULT NULL COMMENT '退款结果说明',
  quota_reduced INT          NOT NULL DEFAULT 0 COMMENT '本次回收的额度数（未发货订单为 0）',
  succ_at       DATETIME     DEFAULT NULL COMMENT '退款完成时间',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_order (out_trade_no),
  KEY idx_wx_refund (wx_refund_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='虚拟支付退款记录表';

-- ============================================================
-- 种子数据：考研的全部节点（新装库执行）
-- ⚠️ 日期核对说明：
--    报名/报名截止/准考证/初试 = 官方已公布口径，仍需上线前再核对一遍；
--    成绩公布/国家线/复试 = 预估值（status=0 未上架），日期确认后再 UPDATE status=1
-- ============================================================
INSERT IGNORE INTO exam (id, name, exam_date, status, app_key, appid, template_id, group_code, group_name, node_type, sort)
VALUES
  (1,  '考研初试',       '2026-12-19', 1, 'kaoyan', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'kaoyan', '考研', 'EXAM',   40),
  (11, '考研报名',       '2026-10-08', 1, 'kaoyan', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'kaoyan', '考研', 'SIGNUP', 10),
  (12, '考研报名截止',   '2026-10-25', 1, 'kaoyan', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'kaoyan', '考研', 'SIGNUP', 20),
  (13, '考研准考证打印', '2026-12-10', 1, 'kaoyan', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'kaoyan', '考研', 'TICKET', 30),
  (15, '考研成绩公布',   '2027-02-24', 0, 'kaoyan', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'kaoyan', '考研', 'SCORE',  50),
  (16, '考研国家线公布', '2027-03-15', 0, 'kaoyan', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'kaoyan', '考研', 'SCORE',  60),
  (17, '考研复试',       '2027-03-28', 0, 'kaoyan', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'kaoyan', '考研', 'RETEST', 70);

-- ============================================================
-- 种子数据：高考的全部节点（2027 年高考）
-- ⚠️ 日期核对说明：
--    高考 6月7日 = 教育部历年固定安排；
--    准考证打印/报名/成绩公布 = 各省口径略有差异，仍需上线前再核对一遍；
--    志愿填报/本科批录取 = 预估值（status=0 未上架），日期确认后再 UPDATE status=1
--    （老库补数据请执行 db/migration-gaokao.sql）
-- ============================================================
INSERT IGNORE INTO exam (id, name, exam_date, status, app_key, appid, template_id, group_code, group_name, node_type, sort)
VALUES
  (201, '高考报名',       '2026-11-01', 1, 'gaokao', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'gaokao', '高考', 'SIGNUP',    10),
  (202, '高考准考证打印', '2027-06-01', 1, 'gaokao', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'gaokao', '高考', 'TICKET',    20),
  (203, '高考',           '2027-06-07', 1, 'gaokao', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'gaokao', '高考', 'EXAM',      30),
  (204, '高考成绩公布',   '2027-06-25', 1, 'gaokao', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'gaokao', '高考', 'SCORE',     40),
  (205, '高考志愿填报',   '2027-06-28', 0, 'gaokao', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'gaokao', '高考', 'VOLUNTEER', 50),
  (206, '本科批录取',     '2027-07-15', 0, 'gaokao', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'gaokao', '高考', 'ADMISSION', 60);

-- ============ 老库升级（已在线上跑过的库）============
-- 不要手动 ALTER 了，直接执行 exam-countdown/migration-v3.sql（含考研+教资全部节点数据）
-- 新库（schema.sql 全新执行）不需要跑迁移脚本。
