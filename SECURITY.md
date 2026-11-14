# 安全策略

## 报告安全问题

请**不要**通过公开 Issue 披露安全问题。可通过以下任一方式私下联系维护者：

- GitHub 私密漏洞报告：仓库 `Security` 标签页 → `Report a vulnerability`
- 邮件：593061846@qq.com（标题请以 `[SECURITY]` 开头）

我们会在 7 个工作日内回复，并在修复发布后于 `CHANGELOG.md` 中致谢（如你希望署名）。

## 范围

以下类型的问题属于本项目的安全范围：

- OpenAPI 签名校验、时间戳/nonce 防重放、凭证管理相关缺陷
- Webhook 回调验签与重试机制缺陷
- 用户/权限模型缺陷（越权访问任务、执行器组授权绕过）
- HTTPS 配置、证书加载、`accessToken` 校验相关缺陷
- GLUE 在线编辑器导致的任意代码执行、路径穿越等问题
- 依赖组件的已知高危漏洞（也可直接提交 Dependabot 升级 PR）

## 部署方自查清单

上线前请至少完成 README「生产上线检查清单」中的全部项目，尤其是：

- 更换默认 `admin / 123456` 口令与 `xxl.job.accessToken`
- 删除建表脚本内置的 `demo_app_key` 示例凭证
- 对外暴露时启用 HTTPS
- OpenAPI 多节点部署需 NTP 对时（nonce 缓存为单机内存，不跨节点共享）
