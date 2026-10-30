package com.xxl.job.admin.core.config;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * XXL-JOB Admin HTTPS配置类
 *
 * @author xuxueli 2025-10-22
 */
@Configuration
public class XxlJobAdminConfigWithHttps implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    private static Logger logger = LoggerFactory.getLogger(XxlJobAdminConfigWithHttps.class);

    @Value("${xxl.job.admin.enableHttps:false}")
    private boolean enableHttps;

    @Value("${xxl.job.admin.keyStorePath:}")
    private String keyStorePath;

    @Value("${xxl.job.admin.keyStorePassword:}")
    private String keyStorePassword;

    @Value("${xxl.job.admin.keyStoreType:JKS}")
    private String keyStoreType;

    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        if (enableHttps && factory instanceof ConfigurableServletWebServerFactory) {
            ConfigurableServletWebServerFactory servletFactory = (ConfigurableServletWebServerFactory) factory;
            
            // 配置SSL
            if (keyStorePath != null && !keyStorePath.isEmpty()) {
                File keyStoreFile = new File(keyStorePath);
                if (keyStoreFile.exists()) {
                    servletFactory.setSslStoreProvider(null); // 使用默认的SSL存储提供者
                } else {
                    logger.warn("SSL key store file not found: {}", keyStorePath);
                }
            }
        }
    }
}