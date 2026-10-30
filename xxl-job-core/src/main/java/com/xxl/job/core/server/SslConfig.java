package com.xxl.job.core.server;

import java.io.InputStream;

/**
 * SSL配置类
 *
 * @author xuxueli 2025-10-22
 */
public class SslConfig {
    private boolean enable;
    private String keyStorePath;
    private String keyStorePassword;
    private String keyStoreType = "JKS";
    private String keyAlias;
    private InputStream keyStoreInputStream;

    public SslConfig() {
    }

    public SslConfig(boolean enable, String keyStorePath, String keyStorePassword) {
        this.enable = enable;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
    }

    public boolean isEnable() {
        return enable;
    }

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public void setKeyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }

    public InputStream getKeyStoreInputStream() {
        return keyStoreInputStream;
    }

    public void setKeyStoreInputStream(InputStream keyStoreInputStream) {
        this.keyStoreInputStream = keyStoreInputStream;
    }
}