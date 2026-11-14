# 贡献指南

感谢你关注 XXL-JOB-PRO。本项目基于 [XXL-JOB](https://github.com/xuxueli/xxl-job) 2.5.0 二次开发，遵循 GPL-3.0 许可。

## 提交前自查

1. 代码可编译：`mvn -q compile -DskipTests`
2. 只构建 admin 相关模块：`mvn -q -pl xxl-job-admin -am package -DskipTests`
3. 新增/修改的接口、配置项，同步更新 `README.md` 或 `doc/` 下的对应文档
4. 不要提交 IDE 配置、`target/`、日志、本地数据库数据与任何真实密钥/证书

## 分支与提交信息

- 从 `main` 切出功能分支：`feat/xxx`、`fix/xxx`、`docs/xxx`
- 提交信息建议使用约定式前缀：`feat(admin): ...`、`fix(openapi): ...`、`docs: ...`、`chore: ...`
- 一个 PR 只做一件事，标题写清改动范围与动机

## Pull Request

- 说明「改了什么 / 为什么 / 如何验证」，关联相关 Issue（`Closes #123`）
- 涉及用户权限、OpenAPI 签名、Webhook、HTTPS 等安全相关改动时，请额外说明兼容性与升级步骤
- 涉及数据库结构的改动，必须同步更新 `doc/db/tables_xxl_job.sql`，并在 PR 中注明老库升级方式

## 代码风格

- Java 8 语法，4 空格缩进，遵循仓库现有命名与分层（`controller` / `core` / `dao` / `openapi`）
- 前端保持 Vue 2 + Element UI 范式，新增页面优先复用 `jobinfo` 页的 `initData` + REST 初始化模式
- 新增前端文案必须先在 `resources/i18n/message_{locale}.properties` 中补齐三个语言版本的 key，否则整页渲染会报错
