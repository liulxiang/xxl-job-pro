# XXL-JOB HTTPS支持使用指南

## 概述

XXL-JOB从v2.5.0版本开始支持HTTPS通信，提升任务调度过程中的数据传输安全性。本文档将详细介绍如何配置和使用HTTPS功能。

## 1. 执行器端HTTPS配置

### 1.1 配置参数说明

在执行器的`application.properties`配置文件中添加以下配置项：

```properties
### xxl-job executor https config
# 是否启用HTTPS
xxl.job.executor.enableHttps=false
# SSL证书路径（支持classpath路径和文件系统路径）
xxl.job.executor.keyStorePath=
# SSL证书密码
xxl.job.executor.keyStorePassword=
# SSL证书类型（JKS、PKCS12等）
xxl.job.executor.keyStoreType=JKS
# SSL证书别名（可选）
xxl.job.executor.keyAlias=
```

### 1.2 配置示例

#### 使用JKS格式证书
```properties
xxl.job.executor.enableHttps=true
xxl.job.executor.keyStorePath=classpath:cert/server.jks
xxl.job.executor.keyStorePassword=123456
xxl.job.executor.keyStoreType=JKS
```

#### 使用PKCS12格式证书
```properties
xxl.job.executor.enableHttps=true
xxl.job.executor.keyStorePath=/path/to/cert/server.p12
xxl.job.executor.keyStorePassword=123456
xxl.job.executor.keyStoreType=PKCS12
```

## 2. 调度中心HTTPS配置

### 2.1 配置参数说明

在调度中心的`application.properties`配置文件中添加以下配置项：

```properties
### xxl-job admin https config
# 是否启用HTTPS
xxl.job.admin.enableHttps=false
# SSL证书路径（支持classpath路径和文件系统路径）
xxl.job.admin.keyStorePath=
# SSL证书密码
xxl.job.admin.keyStorePassword=
# SSL证书类型（JKS、PKCS12等）
xxl.job.admin.keyStoreType=JKS
# SSL证书别名（可选）
xxl.job.admin.keyAlias=
```

### 2.2 Spring Boot内嵌Tomcat HTTPS配置

对于调度中心，还需要在`application.properties`中配置Spring Boot的HTTPS支持：

```properties
# HTTPS端口
server.port=8443
# SSL配置
server.ssl.key-store=classpath:cert/server.jks
server.ssl.key-store-password=123456
server.ssl.key-store-type=JKS
server.ssl.key-alias=tomcat
```

## 3. 证书生成示例

### 3.1 使用keytool生成JKS证书

```bash
# 生成密钥库
keytool -genkeypair -alias tomcat -keyalg RSA -keysize 2048 -storetype JKS -keystore server.jks -validity 3650 -storepass 123456 -keypass 123456

# 导出证书
keytool -export -alias tomcat -keystore server.jks -storepass 123456 -file tomcat.cer

# 查看证书
keytool -list -keystore server.jks -storepass 123456
```

### 3.2 使用openssl生成PKCS12证书

```bash
# 生成私钥
openssl genrsa -out server.key 2048

# 生成证书签名请求
openssl req -new -key server.key -out server.csr

# 生成自签名证书
openssl x509 -req -days 3650 -in server.csr -signkey server.key -out server.crt

# 转换为PKCS12格式
openssl pkcs12 -export -inkey server.key -in server.crt -out server.p12 -password pass:123456
```

## 4. 注意事项

### 4.1 证书路径
- 支持`classpath:`前缀从类路径加载证书
- 支持绝对路径从文件系统加载证书
- 确保证书文件对应用有读取权限

### 4.2 安全建议
- 生产环境应使用有效的SSL证书，而非自签名证书
- 证书密码应妥善保管，建议通过环境变量或配置中心管理
- 定期更新证书，避免过期影响服务

### 4.3 兼容性
- 启用HTTPS后，调度中心和执行器的通信地址会自动使用https协议
- 确保调度中心和执行器都能访问到对应的证书文件
- 如果同时配置了HTTP和HTTPS，建议通过反向代理统一入口

## 5. 故障排除

### 5.1 证书加载失败
- 检查证书路径是否正确
- 确认证书密码是否正确
- 验证证书格式是否与配置的类型匹配

### 5.2 SSL握手失败
- 检查证书是否过期
- 确认证书中的域名是否与访问地址匹配
- 查看日志中的SSL错误信息

### 5.3 端口占用
- 确保HTTPS端口未被其他应用占用
- 检查防火墙设置是否允许HTTPS端口通信