/**
 * 本地开发用 mock 数据（由 db/restore.sql 中的线上真实 exam 数据生成，app_key=main 且 status=1）。
 * 末段「高考」4 个节点为本次新增（对应 db/migration-gaokao.sql 中 status=1 的行）。
 * 仅用于开发环境验证前端，不影响线上/云托管逻辑。
 */
module.exports = [
  {
    "id": 11,
    "name": "考研报名",
    "examDate": "2026-10-08",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "SIGNUP",
    "groupCode": "kaoyan",
    "groupName": "考研"
  },
  {
    "id": 104,
    "name": "教资面试报名",
    "examDate": "2026-11-09",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "SIGNUP",
    "groupCode": "jiaozhi",
    "groupName": "教资"
  },
  {
    "id": 12,
    "name": "考研报名截止",
    "examDate": "2026-10-25",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "SIGNUP",
    "groupCode": "kaoyan",
    "groupName": "考研"
  },
  {
    "id": 13,
    "name": "考研准考证打印",
    "examDate": "2026-12-10",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "TICKET",
    "groupCode": "kaoyan",
    "groupName": "考研"
  },
  {
    "id": 105,
    "name": "教资面试准考证",
    "examDate": "2026-12-01",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "TICKET",
    "groupCode": "jiaozhi",
    "groupName": "教资"
  },
  {
    "id": 1,
    "name": "考研初试",
    "examDate": "2026-12-19",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "EXAM",
    "groupCode": "kaoyan",
    "groupName": "考研"
  },
  {
    "id": 101,
    "name": "中小学教师资格考试（面试）",
    "examDate": "2026-12-05",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "EXAM",
    "groupCode": "jiaozhi",
    "groupName": "教资"
  },
  {
    "id": 102,
    "name": "中小学教师资格考试（上半年笔试）",
    "examDate": "2027-03-06",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "EXAM",
    "groupCode": "jiaozhi",
    "groupName": "教资"
  },
  {
    "id": 103,
    "name": "中小学教师资格考试（上半年面试）",
    "examDate": "2027-05-15",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "EXAM",
    "groupCode": "jiaozhi",
    "groupName": "教资"
  },
  {
    "id": 201,
    "name": "高考报名",
    "examDate": "2026-11-01",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "SIGNUP",
    "groupCode": "gaokao",
    "groupName": "高考"
  },
  {
    "id": 202,
    "name": "高考准考证打印",
    "examDate": "2027-06-01",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "TICKET",
    "groupCode": "gaokao",
    "groupName": "高考"
  },
  {
    "id": 203,
    "name": "高考",
    "examDate": "2027-06-07",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "EXAM",
    "groupCode": "gaokao",
    "groupName": "高考"
  },
  {
    "id": 204,
    "name": "高考成绩公布",
    "examDate": "2027-06-25",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "SCORE",
    "groupCode": "gaokao",
    "groupName": "高考"
  },
  {
    "id": 205,
    "name": "高考志愿填报",
    "examDate": "2027-06-28",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "VOLUNTEER",
    "groupCode": "gaokao",
    "groupName": "高考"
  },
  {
    "id": 206,
    "name": "本科批录取",
    "examDate": "2027-07-15",
    "templateId": "CNGBzwKUOGbHRbLWPucbZsfDdHs_dcpYcK6Sv6gEuws",
    "nodeType": "ADMISSION",
    "groupCode": "gaokao",
    "groupName": "高考"
  }
];
