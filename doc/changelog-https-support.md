# XXL-JOB HTTPS Support Changelog

## v2.5.0 (2025-10-22)

### New Features
- **HTTPS Support**: Added comprehensive HTTPS support for secure communication between the scheduling center and executors
  - Support for SSL/TLS encrypted communication to enhance data transmission security
  - Configuration options for both scheduling center and executors
  - Support for multiple certificate formats including JKS and PKCS12
  - Flexible SSL configuration with customizable key store paths, passwords, and aliases

### Enhancements
- **Security Enhancement**: Enhanced system security by implementing SSL/TLS encrypted communication
- **Configuration Optimization**: Provided flexible HTTPS configuration options for better usability

### Files Modified
- `xxl-job-core/src/main/java/com/xxl/job/core/server/EmbedServer.java`: Added SSL support for Netty server
- `xxl-job-core/src/main/java/com/xxl/job/core/server/SslConfig.java`: Created SSL configuration class
- `xxl-job-core/src/main/java/com/xxl/job/core/executor/XxlJobExecutor.java`: Added HTTPS configuration options
- `xxl-job-core/src/main/java/com/xxl/job/core/executor/impl/XxlJobSpringExecutor.java`: Updated Spring executor
- `xxl-job-executor-sample-springboot/src/main/java/com/xxl/job/executor/core/config/XxlJobConfig.java`: Added HTTPS configuration properties
- `xxl-job-executor-sample-springboot/src/main/resources/application.properties`: Added HTTPS configuration options
- `xxl-job-admin/src/main/resources/application.properties`: Added HTTPS configuration options
- `xxl-job-admin/src/main/java/com/xxl/job/admin/core/config/XxlJobAdminConfigWithHttps.java`: Created HTTPS configuration class for admin
- `xxl-job-admin/src/main/java/com/xxl/job/admin/core/conf/XxlJobAdminConfig.java`: Added HTTPS configuration properties
- `doc/https-support.md`: Created comprehensive HTTPS usage guide
- `doc/XXL-JOB官方文档.md`: Updated feature list
- `doc/XXL-JOB-English-Documentation.md`: Updated feature list and version history
- `xxl-job-core/pom.xml`: Added Netty handler dependency

### Configuration
To enable HTTPS, add the following properties to your configuration:

For executors:
```properties
xxl.job.executor.enableHttps=true
xxl.job.executor.keyStorePath=/path/to/keystore.jks
xxl.job.executor.keyStorePassword=your_password
xxl.job.executor.keyStoreType=JKS
xxl.job.executor.keyAlias=your_alias
```

For scheduling center:
```properties
xxl.job.admin.enableHttps=true
xxl.job.admin.keyStorePath=/path/to/keystore.jks
xxl.job.admin.keyStorePassword=your_password
xxl.job.admin.keyStoreType=JKS
xxl.job.admin.keyAlias=your_alias
```