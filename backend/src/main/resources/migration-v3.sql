-- ============================================================
-- migration-v3.sql：老库升级到「多考试 / 多节点」结构
-- 适用：已经跑过 v1/v2 的线上库（exam 表只有 exam_date + signup_date/ticket_date）
-- 新库直接执行 schema.sql 即可，不需要本脚本。
--
-- 内容来源：从线上 exam_countdown 库导出的真实数据（12 行节点）。
-- 幂等：ADD COLUMN 用 IF NOT EXISTS，数据行用 REPLACE / ON DUPLICATE KEY。
-- ⚠️ 执行前先备份：mysqldump exam_countdown > backup_$(date +%F).sql
-- ============================================================

-- ---------- 1. 加列 ----------
ALTER TABLE exam ADD COLUMN IF NOT EXISTS app_key     VARCHAR(32) NULL COMMENT '矩阵化：所属小程序标识 kaoyan/jiaozhi';
ALTER TABLE exam ADD COLUMN IF NOT EXISTS appid       VARCHAR(32) NULL COMMENT '矩阵化：所属小程序 AppID';
ALTER TABLE exam ADD COLUMN IF NOT EXISTS template_id VARCHAR(64) NULL COMMENT '矩阵化：该小程序的订阅消息模板ID（空则用全局默认）';
ALTER TABLE exam ADD COLUMN IF NOT EXISTS group_code  VARCHAR(32) NULL COMMENT '多考试：考试大类标识 kaoyan/jiaozhi/cet/gongkao';
ALTER TABLE exam ADD COLUMN IF NOT EXISTS group_name  VARCHAR(32) NULL COMMENT '多考试：大类显示名（考研/教资/四六级...）';
ALTER TABLE exam ADD COLUMN IF NOT EXISTS node_type   VARCHAR(16) NULL COMMENT '节点类型 SIGNUP报名 TICKET准考证 EXAM考试 SCORE成绩/分数线 RETEST复试面试';
ALTER TABLE exam ADD COLUMN IF NOT EXISTS sort        INT DEFAULT 0   COMMENT '同组内展示顺序';

-- ---------- 2. 考研：7 个节点 ----------
REPLACE INTO exam (id, name, exam_date, signup_date, ticket_date, status, app_key, appid, template_id, group_code, group_name, node_type, sort) VALUES
  (1,  '考研初试',       '2026-12-19', '2026-10-08', '2026-12-10', 1, 'main', 'wx23a7083f915b62a1', 'qD0l3QHzRu-MOJl8A21mJxOzD-ykunkk-IjmGR-AJvw', 'kaoyan', '考研', 'EXAM',   40),
  (11, '考研报名',       '2026-10-08', NULL,         NULL,         1, 'main', 'wx23a7083f915b62a1', 'qD0l3QHzRu-MOJl8A21mJxOzD-ykunkk-IjmGR-AJvw', 'kaoyan', '考研', 'SIGNUP', 10),
  (12, '考研报名截止',   '2026-10-25', NULL,         NULL,         1, 'main', 'wx23a7083f915b62a1', 'qD0l3QHzRu-MOJl8A21mJxOzD-ykunkk-IjmGR-AJvw', 'kaoyan', '考研', 'SIGNUP', 20),
  (13, '考研准考证打印', '2026-12-10', NULL,         NULL,         1, 'main', 'wx23a7083f915b62a1', 'qD0l3QHzRu-MOJl8A21mJxOzD-ykunkk-IjmGR-AJvw', 'kaoyan', '考研', 'TICKET', 30),
  (15, '考研成绩公布',   '2027-02-24', NULL,         NULL,         0, 'main', 'wx23a7083f915b62a1', 'qD0l3QHzRu-MOJl8A21mJxOzD-ykunkk-IjmGR-AJvw', 'kaoyan', '考研', 'SCORE',  50),
  (16, '考研国家线公布', '2027-03-15', NULL,         NULL,         0, 'main', 'wx23a7083f915b62a1', 'qD0l3QHzRu-MOJl8A21mJxOzD-ykunkk-IjmGR-AJvw', 'kaoyan', '考研', 'SCORE',  60),
  (17, '考研复试',       '2027-03-28', NULL,         NULL,         0, 'main', 'wx23a7083f915b62a1', 'qD0l3QHzRu-MOJl8A21mJxOzD-ykunkk-IjmGR-AJvw', 'kaoyan', '考研', 'RETEST', 70);

-- ---------- 3. 教资：5 个节点 ----------
REPLACE INTO exam (id, name, exam_date, signup_date, ticket_date, status, app_key, appid, template_id, group_code, group_name, node_type, sort) VALUES
  (101, '中小学教师资格考试（面试）',         '2026-12-05', '2026-11-09', '2026-12-01', 1, 'main', 'wx23a7083f915b62a1', 'qD0l3QHzRu-MOJl8A21mJxOzD-ykunkk-IjmGR-AJvw', 'jiaozhi', '教资', 'EXAM',   40),
  (102, '中小学教师资格考试（上半年笔试）',   '2027-03-06', '2027-01-07', '2027-03-02', 1, 'main', 'wx23a7083f915b62a1', 'qD0l3QHzRu-MOJl8A21mJxOzD-ykunkk-IjmGR-AJvw', 'jiaozhi', '教资', 'EXAM',   40),
  (103, '中小学教师资格考试（上半年面试）',   '2027-05-15', '2027-04-21', '2027-05-12', 1, 'main', 'wx23a7083f915b62a1', 'qD0l3QHzRu-MOJl8A21mJxOzD-ykunkk-IjmGR-AJvw', 'jiaozhi', '教资', 'EXAM',   40),
  (104, '教资面试报名',                       '2026-11-09', NULL,         NULL,         1, 'main', 'wx23a7083f915b62a1', 'qD0l3QHzRu-MOJl8A21mJxOzD-ykunkk-IjmGR-AJvw', 'jiaozhi', '教资', 'SIGNUP', 10),
  (105, '教资面试准考证',                     '2026-12-01', NULL,         NULL,         1, 'main', 'wx23a7083f915b62a1', 'qD0l3QHzRu-MOJl8A21mJxOzD-ykunkk-IjmGR-AJvw', 'jiaozhi', '教资', 'TICKET', 30);
