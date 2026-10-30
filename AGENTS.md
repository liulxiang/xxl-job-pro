# AGENTS.md

This file provides guidance to Lingma (lingma.aliyun.com) when working with code in this repository.

## 项目概览

XXL-JOB 分布式任务调度平台的定制分支（xxl-job-pro，版本 2.5.0），在原版基础上主要增强了 Admin 的 HTTPS 支持与 OpenAPI（远程创建/触发任务）。技术栈：Java 8、Spring Boot 2.7.18、Spring MVC、MyBatis、FreeMarker、MySQL。

## 常用命令

所有命令在仓库根 `/Users/mac/Documents/eyang/xxl-job-pro` 执行（多模块 Maven 聚合工程）。

```bash
# 编译全部模块
mvn -q compile -DskipTests

# 只构建 admin 及其依赖模块（core）
mvn -q -pl xxl-job-admin -am package -DskipTests

# 运行 admin（打包后的可执行 fat jar，默认端口 8080，context-path /xxl-job-admin）
java -jar xxl-job-admin/target/xxl-job-admin-2.5.0.jar
# 覆盖端口/配置示例
java -jar xxl-job-admin/target/xxl-job-admin-2.5.0.jar --server.port=8090

# 运行单个测试类 / 单个方法（测试依赖可用的 MySQL）
mvn -pl xxl-job-admin test -Dtest=JobInfoControllerTest
mvn -pl xxl-job-admin test -Dtest=XxlJobInfoDaoTest#testMethod
```

注意：`spring-boot:run` 插件前缀在聚合根无法解析；请用 `package` 后 `java -jar`，或 `cd xxl-job-admin` 再 `mvn spring-boot:run`（需先 `mvn -pl xxl-job-core install`）。

## 运行前置

- 先导入数据库脚本 `doc/db/tables_xxl_job.sql`（库名 `xxl_job`）。
- `xxl-job-admin/src/main/resources/application.yml` 配置 datasource、`xxl.job.accessToken`、`xxl.job.i18n`（zh_CN/zh_TC/en）、HTTPS 相关项（`xxl.job.admin.enableHttps` 等）。该文件由 `application.properties` 迁移而来，键名拼写（含 `accessToken`、`keyStorePath` 等 camelCase）保持与原文件 1:1 一致，因 `XxlJobAdminConfig` 通过 `@Value` 按精确键名注入。
- 自定义 xxl-job 配置项集中在 `xxl.job.*` 下；`spring.mail.properties.*` 的值需用引号写成字符串（绑定目标是无类型的 `Properties` Map）。
- Admin 默认登录：admin / 123456（role=1 管理员）。

## 模块与架构

三个 Maven 模块（见根 `pom.xml` 的 `<modules>`）：

- **xxl-job-core**：执行器与调度器共享内核。`biz/model` 里的 `ReturnT`、`AdminBiz`/`ExecutorBiz` 是两侧 RPC 契约；`enums`、`glue`、`handler`、`context` 等为执行器侧。
- **xxl-job-admin**：调度中心（Web）。核心包结构（`com.xxl.job.admin`）：
  - `controller`：MVC 控制器。视图名不含后缀（由 `spring.freemarker.suffix` 决定）；`@ResponseBody` 接口返回 `ReturnT` JSON（成功 `code==200`）或分页 Map（`{recordsTotal, data}`）。
  - `controller.interceptor`：`PermissionInterceptor` 依据 `@PermissionLimit`（limit/adminuser）做登录与管理员校验，并把登录用户写入 request 属性 `LoginService.LOGIN_IDENTITY_KEY`；未登录时非 JSON 请求 302 到 `/toLogin`。
  - `controller.resolver.WebExceptionResolver`：统一异常 → 带 `@ResponseBody` 的方法返回 JSON，否则返回 `common/common.exception` 视图。
  - `core.thread`：调度核心线程（`JobScheduleHelper` 时间轮/预取、`JobTriggerPoolHelper` 快慢触发池、`JobFailMonitorHelper` 失败重试与告警、`JobRegistryHelper` 执行器注册/摘除、`JobCompleteHelper` 回调、`JobLogReportHelper` 报表）。
  - `core.route.strategy`：9 种路由策略；`core.scheduler`：`ScheduleTypeEnum`/`MisfireStrategyEnum`；`core.cron`：`CronExpression`。
  - `dao` + `resources/mybatis-mapper/*.xml`：数据访问层（表 `xxl_job_info/group/log/log_glue/log_report/registry/user/openapi_callback/api_client`）。
  - `openapi`：对外 API 鉴权与签名（`OpenApiSignUtil`、`OpenApiAuthInterceptor`、防重放 `OpenApiNonceCache`、webhook 回调 `OpenApiWebhookNotifier`）。
- **xxl-job-executor-sample-springboot**：执行器示例。

## 前端约定（重要，进行中）

Admin 页面正在从 FreeMarker 迁移到 **Vue2 + Element UI**，参考 `noodle-paw-admin` 的 `stage/*.html` 范式。当前约定：

- 模板后缀为 `.html`（`spring.freemarker.suffix=.html`），`resources/templates/**/*.ftl` 已全部重命名为 `.html`；页面内 `<#import "../common/common.macro.html">` 等引用同步。
- 保留最小化 FreeMarker：公共 head 宏 `templates/common/common.macro.html` 负责加载 jQuery / layer / Vue(`static/plugins/vue/vue.min.js`) / Element UI(`static/plugins/element-ui/lib/index.js` + theme-chalk) 并注入 `base_url` 与 `I18n`；header/left/footer 仍用 FreeMarker 宏读取登录身份做角色菜单。页面内容区用 Vue2 + Element UI，不再用 `<#list>` 渲染下拉/表格。
- 原本由 `model.addAttribute` 下发到页面的枚举字典、执行器列表、登录用户等，改为各页 `created` 拉取的 `@ResponseBody` `initData` 接口（见 `JobInfoController.initData`，Vue 页面范式已验证：`jobinfo`）。
- **新增/编辑走既有 POST 表单接口**：jQuery 会把 `id:null` 序列化为空串导致 `int id` 绑定 400 —— 新增时须从载荷删除空 id。cron 表达式沿用遗留 `cronGen` jQuery 插件（非受控 input，打开弹窗前重建容器避免克隆节点累加）。

## i18n

前端文案通过 `I18n` 全局对象（`core.util.I18nUtil.getMultString()` 注入）取 `resources/i18n/message_{locale}.properties`。在 Vue 模板中 `${I18n.key}` 是 FreeMarker 插值，**引用不存在的 key 会导致整页渲染报错**，新增页面前应校验 key 是否存在。
