-- ============================================================
-- migration-gaokao.sql —— 新增「高考」考试大类（2027 年高考）
-- 适用：已跑过 migration-v3 的线上库 / 本地开发库
-- 幂等：REPLACE INTO，可重复执行
-- ⚠️ 执行前先备份：mysqldump -u root -p exam_countdown > backup_$(date +%F).sql
--
-- 日期口径说明：
--   高考           6月7日       —— 教育部历年固定安排（部分省份 6月9-10日有选考科目）
--   准考证打印     考前一周     —— 各省多为 6 月 1 日起
--   报名           11月1日起    —— ⚠️ 各省差异大（多在 10 月底~11 月中旬），请按本省口径修正
--   成绩公布       6月25日左右  —— 多为 6 月 23-25 日，以本省考试院公告为准
--   ↓ 以下两条为预估值，先以 status=0 存库不上架，日期确认后 UPDATE status=1 即可
--   志愿填报       6月28日起
--   本科批录取     7月15日起
-- ============================================================

REPLACE INTO exam (id, name, exam_date, status, app_key, appid, template_id, group_code, group_name, node_type, sort) VALUES
  (201, '高考报名',       '2026-11-01', 1, 'main', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'gaokao', '高考', 'SIGNUP',    10),
  (202, '高考准考证打印', '2027-06-01', 1, 'main', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'gaokao', '高考', 'TICKET',    20),
  (203, '高考',           '2027-06-07', 1, 'main', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'gaokao', '高考', 'EXAM',      30),
  (204, '高考成绩公布',   '2027-06-25', 1, 'main', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'gaokao', '高考', 'SCORE',     40),
  (205, '高考志愿填报',   '2027-06-28', 0, 'main', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'gaokao', '高考', 'VOLUNTEER', 50),
  (206, '本科批录取',     '2027-07-15', 0, 'main', 'wx23a7083f915b62a1', 'CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws', 'gaokao', '高考', 'ADMISSION', 60);

-- 核对（应返回 6 行，其中上架 4 行）
-- SELECT id, name, exam_date, status, group_name, node_type FROM exam WHERE group_code = 'gaokao' ORDER BY sort;

-- 预估节点日期确认后上架：
-- UPDATE exam SET status = 1 WHERE id IN (205, 206);
