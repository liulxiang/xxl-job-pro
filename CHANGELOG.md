# 变更记录

本文件记录本项目相对上游 XXL-JOB 的功能变更。上游版本的变更请参考 [XXL-JOB Releases](https://github.com/xuxueli/xxl-job/releases)。

## 2.5.0 — 2025

首个开源版本，基线为 XXL-JOB 2.5.0。

### 新增

- **数据库化用户与权限**：用户存库（初始化 `admin / 123456`），管理员/普通用户两种角色，按执行器组维度授权
- **开放 API**：`/openapi/v1` 机器接口，`AppKey + HMAC-SHA256 签名 + 时间戳 + nonce 防重放`，凭证在「开放API凭证」页面维护
- **OpenAPI Java SDK**：`xxl-job-core` 内置 `OpenApiClient`，自动处理签名、时间戳与 nonce
- **Webhook 回调**：任务触发后回调业务方，带 `Xxl-Job-Callback-Sign` 验签头，失败按退避策略最多重试 5 次，集群下以 `next_retry_time` 乐观认领避免重复投递
- **HTTPS 支持**：调度中心与执行器均可开启 HTTPS（JKS / PKCS12），详见 [doc/https-support.md](doc/https-support.md)
- **内置使用教程页**：包含 OpenAPI 接口清单、字段说明、Webhook 规范与在线签名调试器

### 变更

- 后台页面由 FreeMarker 模板升级为 Vue 2.6.14 + Element UI，初始化数据统一走 REST 接口（如 `/dashboard/initData`）
- `application.properties` 迁移为 `application.yml`，键名与原拼写保持 1:1
- 调度中心默认端口调整为 `8081`

### 数据库

- 新增表：`xxl_job_api_client`（开放API凭证）、`xxl_job_openapi_callback`（回调与重试状态）
- 老库升级需补建上述两张表，建表语句见 [doc/db/tables_xxl_job.sql](doc/db/tables_xxl_job.sql)
