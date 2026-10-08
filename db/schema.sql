-- ============================================================
-- 考试倒计时小程序 · 线上库表结构（来自云托管 MySQL 导出）
-- 库名：exam_countdown
-- 说明：重建工程的权威数据模型；AUTO_INCREMENT 值反映线上真实数据量
-- ============================================================

-- 考试表（105 条数据：AUTO_INCREMENT=106）
CREATE TABLE `exam` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '考试名称',
  `exam_date` date NOT NULL COMMENT '考试日期',
  `signup_date` date DEFAULT NULL COMMENT '报名日期',
  `ticket_date` date DEFAULT NULL COMMENT '准考证打印日期',
  `status` tinyint DEFAULT '1' COMMENT '1上架 0下架',
  `app_key` varchar(32) DEFAULT NULL COMMENT '矩阵化：所属小程序标识 kaoyan/jiaozhi',
  `appid` varchar(32) DEFAULT NULL COMMENT '矩阵化：所属小程序 AppID',
  `template_id` varchar(64) DEFAULT NULL COMMENT '矩阵化：该小程序的订阅消息模板ID',
  `group_code` varchar(32) DEFAULT NULL COMMENT '考试大类标识 kaoyan/jiaozhi/cet/gongkao',
  `group_name` varchar(32) DEFAULT NULL COMMENT '大类显示名（考研/教资...）',
  `node_type` varchar(16) DEFAULT NULL COMMENT '节点类型 SIGNUP/TICKET/EXAM/SCORE/RETEST',
  `sort` int DEFAULT '0' COMMENT '同组内展示顺序',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=106 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试表';

-- 推送日志表（104 条：AUTO_INCREMENT=105）
CREATE TABLE `push_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `openid` varchar(64) NOT NULL,
  `exam_id` bigint NOT NULL,
  `node_name` varchar(16) DEFAULT NULL COMMENT '提醒节点: D30/D7/D1/DAY',
  `result` varchar(16) DEFAULT NULL COMMENT 'OK/FAIL',
  `err_msg` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=105 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='推送日志';

-- 订阅配额表（3 条：AUTO_INCREMENT=4）
-- 微信订阅消息为「一次授权 = 一次推送」，故用 remain_times 记额度
CREATE TABLE `subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `openid` varchar(64) NOT NULL,
  `exam_id` bigint NOT NULL,
  `template_id` varchar(64) NOT NULL COMMENT '订阅消息模板ID',
  `remain_times` int NOT NULL DEFAULT '1' COMMENT '剩余可推送次数',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_exam` (`exam_id`,`remain_times`),
  KEY `idx_open` (`openid`,`exam_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订阅配额表（一次授权=1次推送额度）';

-- 用户表（2 条：AUTO_INCREMENT=3）
CREATE TABLE `wx_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `openid` varchar(64) NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `openid` (`openid`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
