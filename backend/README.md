# exam-countdown（后端）

考试节点倒计时 + 微信订阅消息提醒服务。原运行环境：微信云托管，服务名 `exam-backend`，镜像版本 `023`（2026-09-30 发布）。

## 技术栈（与线上一致，已从 jar 内依赖逐一核对）

| 组件 | 版本 |
|---|---|
| Java | 17（`Build-Jdk-Spec: 17`） |
| Spring Boot | 3.3.4 |
| MyBatis-Plus | 3.5.7（`mybatis-plus-spring-boot3-starter`） |
| MySQL 驱动 | `mysql-connector-j` 8.3.0 |
| 内嵌 Tomcat | 10.1.30 |
| Lombok | 1.18.34 |
| 构建 | Maven JAR Plugin 3.4.2，产物 `app.jar` |

HTTP 调用只用 JDK 自带 `java.net.http.HttpClient`，**没有引入任何第三方 HTTP 客户端**。

## 目录

```
backend/
├── pom.xml
└── src/main/
    ├── java/com/exam/countdown/
    │   ├── ExamCountdownApplication.java     # @EnableScheduling + @MapperScan
    │   ├── config/CorsConfig.java            # /api/** 放通跨域
    │   ├── controller/
    │   │   ├── AuthController.java           # POST /api/auth/login
    │   │   ├── ExamController.java           # GET  /api/exams
    │   │   ├── SubscribeController.java      # POST /api/subscribe
    │   │   └── JobController.java            # GET  /api/job/remind
    │   ├── dto/                              # LoginReq / LoginResp / SubscribeReq（Lombok @Data）
    │   ├── entity/                           # Exam / PushLog / Subscription / WxUser
    │   ├── job/RemindJob.java                # 定时扫描 + 推送
    │   ├── mapper/                           # 4 个 BaseMapper
    │   └── service/
    │       ├── TokenManager.java             # access_token 缓存
    │       └── WxMessageService.java         # 云调用 / 公网双通道发送
    └── resources/
        ├── application.yml                   # 全部配置走环境变量覆盖
        ├── schema.sql                        # 新库建表 + 考研种子数据
        └── migration-v3.sql                  # 老库升级（考研+教资全量节点）
```

## 构建

```bash
cd backend
mvn clean package -DskipTests
# 产物：target/app.jar
```

## 运行（本地）

```bash
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3306/exam_countdown?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=<your-password>
export WX_APPID=<appid>
export WX_SECRET=<secret>
export WX_TEMPLATE_ID=<template-id>
export WX_CLOUD_CALL=false        # 本地没有云托管网关，走公网 access_token
export REMIND_JOB_TOKEN=<random-token>

java -Xmx400m -jar target/app.jar
```

## 部署到云托管（与原环境保持一致）

1. **镜像/启动命令**：`java -Xmx400m -jar app.jar`（工作目录 `/app`），监听 `8080`
2. **环境变量**（控制台配置，不要写进代码）：
   - `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`
   - `WX_APPID` / `WX_SECRET` / `WX_TEMPLATE_ID`
   - `WX_CLOUD_CALL=true`（云托管内网会自动注入 token，无需自己换）
   - `REMIND_JOB_TOKEN`
3. **定时触发**：可用应用内 `@Scheduled`（默认每天 08:00），也可在云托管配置定时任务请求
   `GET /api/job/remind?token=<REMIND_JOB_TOKEN>`
4. **数据库初始化**：新库执行 `resources/schema.sql`；老库执行 `resources/migration-v3.sql`

> ⚠️ 云托管会在容器内注入 `cert/` 目录 + `/etc/hosts` 改写 + iptables 转发（`169.254.10.1 → 127.0.0.1:29081/29444`），
> 那是平台自身的微信接口内网代理机制，**与业务代码无关，不需要重建**。

## 安全事项（必做）

1. 重置小程序 **AppSecret**（旧值曾在明文环境变量中暴露）
2. 轮换云托管 **数据库密码** 与 **`REMIND_JOB_TOKEN`**，并同步更新环境变量
3. `application.yml` 中默认值已全部清空为占位，**不要**再把真实凭据提交进仓库
