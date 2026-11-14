## 改动内容

<!-- 一句话说明这个 PR 做了什么 -->

## 动机 / 关联 Issue

<!-- 为什么需要这个改动；Closes #123 -->

## 改动范围

- [ ] xxl-job-core
- [ ] xxl-job-admin（后端）
- [ ] xxl-job-admin（前端 Vue2 + Element UI）
- [ ] 文档（README / doc/）
- [ ] 构建 / CI

## 验证方式

<!-- 复现与验证步骤，例如：导入建表脚本 → 启动 admin → 调用 /job/add 观察返回 -->

```
mvn -q compile -DskipTests
```

## 兼容性与影响

- [ ] 无破坏性变更
- [ ] 含数据库结构变更（已更新 `doc/db/tables_xxl_job.sql`，并说明老库升级方式）
- [ ] 含配置项变更（已更新 README「配置说明」）
- [ ] 涉及安全相关逻辑（签名 / 验签 / 权限 / HTTPS），已说明风险与回归点

## 自查

- [ ] 未提交真实密钥、证书、`accessToken`、`AppSecret`
- [ ] 新增前端文案已在 `message_zh_CN / zh_TC / en` 三处补齐
- [ ] 已同步更新相关文档
