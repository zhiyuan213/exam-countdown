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
