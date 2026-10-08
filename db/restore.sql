-- ============================================================
-- restore.sql —— 一次性重建 exam_countdown 库（结构 + 线上真实数据）
-- 生成来源：线上 023 版 app.jar 内的 schema.sql  +  腾讯云 DMC 导出的 4 张表数据
-- 用法：mysql -u root -p < restore.sql
-- 说明：数据为开发期自测数据（2 个真实用户、3 条订阅、104 条推送日志）
-- ============================================================
CREATE DATABASE IF NOT EXISTS `exam_countdown` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `exam_countdown`;

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

-- ============ 数据 ============

-- ---- exam ----
INSERT INTO `exam_countdown`.`exam` (`id`,`name`,`exam_date`,`signup_date`,`ticket_date`,`status`,`app_key`,`appid`,`template_id`,`group_code`,`group_name`,`node_type`,`sort`) VALUES (1,'考研初试','2026-12-19','2026-10-08','2026-12-10',1,'main','wx23a7083f915b62a1','CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws','kaoyan','考研','EXAM',40);
INSERT INTO `exam_countdown`.`exam` (`id`,`name`,`exam_date`,`status`,`app_key`,`appid`,`template_id`,`group_code`,`group_name`,`node_type`,`sort`) VALUES (11,'考研报名','2026-10-08',1,'main','wx23a7083f915b62a1','CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws','kaoyan','考研','SIGNUP',10);
INSERT INTO `exam_countdown`.`exam` (`id`,`name`,`exam_date`,`status`,`app_key`,`appid`,`template_id`,`group_code`,`group_name`,`node_type`,`sort`) VALUES (12,'考研报名截止','2026-10-25',1,'main','wx23a7083f915b62a1','CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws','kaoyan','考研','SIGNUP',20);
INSERT INTO `exam_countdown`.`exam` (`id`,`name`,`exam_date`,`status`,`app_key`,`appid`,`template_id`,`group_code`,`group_name`,`node_type`,`sort`) VALUES (13,'考研准考证打印','2026-12-10',1,'main','wx23a7083f915b62a1','CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws','kaoyan','考研','TICKET',30);
INSERT INTO `exam_countdown`.`exam` (`id`,`name`,`exam_date`,`status`,`app_key`,`appid`,`template_id`,`group_code`,`group_name`,`node_type`,`sort`) VALUES (15,'考研成绩公布','2027-02-24',0,'main','wx23a7083f915b62a1','CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws','kaoyan','考研','SCORE',50);
INSERT INTO `exam_countdown`.`exam` (`id`,`name`,`exam_date`,`status`,`app_key`,`appid`,`template_id`,`group_code`,`group_name`,`node_type`,`sort`) VALUES (16,'考研国家线公布','2027-03-15',0,'main','wx23a7083f915b62a1','CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws','kaoyan','考研','SCORE',60);
INSERT INTO `exam_countdown`.`exam` (`id`,`name`,`exam_date`,`status`,`app_key`,`appid`,`template_id`,`group_code`,`group_name`,`node_type`,`sort`) VALUES (17,'考研复试','2027-03-28',0,'main','wx23a7083f915b62a1','CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws','kaoyan','考研','RETEST',70);
INSERT INTO `exam_countdown`.`exam` (`id`,`name`,`exam_date`,`signup_date`,`ticket_date`,`status`,`app_key`,`appid`,`template_id`,`group_code`,`group_name`,`node_type`,`sort`) VALUES (101,'中小学教师资格考试（面试）','2026-12-05','2026-11-09','2026-12-01',1,'main','wx23a7083f915b62a1','CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws','jiaozhi','教资','EXAM',40);
INSERT INTO `exam_countdown`.`exam` (`id`,`name`,`exam_date`,`signup_date`,`ticket_date`,`status`,`app_key`,`appid`,`template_id`,`group_code`,`group_name`,`node_type`,`sort`) VALUES (102,'中小学教师资格考试（上半年笔试）','2027-03-06','2027-01-07','2027-03-02',1,'main','wx23a7083f915b62a1','CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws','jiaozhi','教资','EXAM',40);
INSERT INTO `exam_countdown`.`exam` (`id`,`name`,`exam_date`,`signup_date`,`ticket_date`,`status`,`app_key`,`appid`,`template_id`,`group_code`,`group_name`,`node_type`,`sort`) VALUES (103,'中小学教师资格考试（上半年面试）','2027-05-15','2027-04-21','2027-05-12',1,'main','wx23a7083f915b62a1','CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws','jiaozhi','教资','EXAM',40);
INSERT INTO `exam_countdown`.`exam` (`id`,`name`,`exam_date`,`status`,`app_key`,`appid`,`template_id`,`group_code`,`group_name`,`node_type`,`sort`) VALUES (104,'教资面试报名','2026-11-09',1,'main','wx23a7083f915b62a1','CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws','jiaozhi','教资','SIGNUP',10);
INSERT INTO `exam_countdown`.`exam` (`id`,`name`,`exam_date`,`status`,`app_key`,`appid`,`template_id`,`group_code`,`group_name`,`node_type`,`sort`) VALUES (105,'教资面试准考证','2026-12-01',1,'main','wx23a7083f915b62a1','CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws','jiaozhi','教资','TICKET',30);

-- ---- wx_user ----
INSERT INTO `exam_countdown`.`wx_user` (`id`,`openid`,`created_at`) VALUES (1,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts','2026-09-24 12:41:19');
INSERT INTO `exam_countdown`.`wx_user` (`id`,`openid`,`created_at`) VALUES (2,'oHKYGxrXxHig5xjrDood2ssrquVc','2026-09-25 03:24:40');

-- ---- subscription ----
INSERT INTO `exam_countdown`.`subscription` (`id`,`openid`,`exam_id`,`template_id`,`remain_times`,`created_at`) VALUES (2,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws',0,'2026-09-24 12:41:19');
INSERT INTO `exam_countdown`.`subscription` (`id`,`openid`,`exam_id`,`template_id`,`remain_times`,`created_at`) VALUES (3,'oHKYGxrXxHig5xjrDood2ssrquVc',101,'yHgEQ0G-j_wYVI9D85PJa4WNursx3WsaD4Wgm_I8uE4',0,'2026-09-25 03:28:52');

-- ---- push_log ----
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (25,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=-1','2026-09-24 12:42:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (26,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:42:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (27,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=-1','2026-09-24 12:43:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (28,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:43:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (29,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=-1','2026-09-24 12:44:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (30,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:44:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (31,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=-1','2026-09-24 12:45:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (32,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:45:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (33,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:45:02');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (34,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=-1','2026-09-24 12:46:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (35,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:46:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (36,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:46:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (37,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=-1','2026-09-24 12:47:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (38,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:47:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (39,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:47:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (40,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=-1','2026-09-24 12:48:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (41,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:48:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (42,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:48:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (43,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=-1','2026-09-24 12:49:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (44,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:49:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (45,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:49:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (46,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=-1','2026-09-24 12:50:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (47,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:50:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (48,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:50:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (49,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=-1','2026-09-24 12:51:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (50,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:51:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (51,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:51:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (52,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=-1','2026-09-24 12:52:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (53,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:52:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (54,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:52:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (55,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:53:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (56,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:53:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (57,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:54:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (58,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:54:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (59,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:55:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (60,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:55:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (61,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:56:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (62,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:56:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (63,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:57:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (64,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:57:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (65,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:58:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (66,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:58:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (67,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:59:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (68,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 12:59:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (69,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:00:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (70,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:00:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (71,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:01:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (72,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:01:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (73,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:02:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (74,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:02:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (75,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:03:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (76,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:03:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (77,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:04:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (78,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:04:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (79,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:04:02');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (80,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:05:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (81,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:05:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (82,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:05:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (83,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:06:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (84,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:06:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (85,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:07:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (86,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:07:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (87,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:08:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (88,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:08:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (89,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:09:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (90,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:09:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (91,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:10:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (92,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','FAIL','errcode=47003','2026-09-24 13:10:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`created_at`) VALUES (93,'oz140xqXTNBrY7v7HWe_6rUfZ4Ts',1,'D1','OK','2026-09-24 13:10:02');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (94,'oHKYGxrXxHig5xjrDood2ssrquVc',101,'D1','FAIL','errcode=40003','2026-09-25 03:35:02');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (95,'oHKYGxrXxHig5xjrDood2ssrquVc',101,'D1','FAIL','errcode=40003','2026-09-25 03:36:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (96,'oHKYGxrXxHig5xjrDood2ssrquVc',101,'D1','FAIL','errcode=40003','2026-09-25 03:37:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (97,'oHKYGxrXxHig5xjrDood2ssrquVc',101,'D1','FAIL','errcode=40003','2026-09-25 03:38:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (98,'oHKYGxrXxHig5xjrDood2ssrquVc',101,'D1','FAIL','errcode=40003','2026-09-25 03:39:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (99,'oHKYGxrXxHig5xjrDood2ssrquVc',101,'D1','FAIL','errcode=40003','2026-09-25 03:40:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (100,'oHKYGxrXxHig5xjrDood2ssrquVc',101,'D1','FAIL','errcode=40003','2026-09-25 03:41:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (101,'oHKYGxrXxHig5xjrDood2ssrquVc',101,'D1','FAIL','errcode=40003','2026-09-25 03:42:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (102,'oHKYGxrXxHig5xjrDood2ssrquVc',101,'D1','FAIL','errcode=40003','2026-09-25 03:43:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`err_msg`,`created_at`) VALUES (103,'oHKYGxrXxHig5xjrDood2ssrquVc',101,'D1','FAIL','errcode=40003','2026-09-25 03:44:00');
INSERT INTO `exam_countdown`.`push_log` (`id`,`openid`,`exam_id`,`node_name`,`result`,`created_at`) VALUES (104,'oHKYGxrXxHig5xjrDood2ssrquVc',101,'D1','OK','2026-09-25 03:44:02');
