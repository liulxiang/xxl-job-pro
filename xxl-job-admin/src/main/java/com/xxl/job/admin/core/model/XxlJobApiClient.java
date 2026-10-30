package com.xxl.job.admin.core.model;

import java.util.Date;

/**
 * 开放API接入客户端（应用级凭证）
 *
 * <p>每个接入的异构项目分配一对 AppKey/AppSecret，并绑定一个 {@link XxlJobUser} 账号，
 * 复用其 role/permission 做执行器级别的权限校验。</p>
 *
 * @author xxl-job-pro
 */
public class XxlJobApiClient {

	private int id;
	private String appKey;		// 接入凭证 AppKey
	private String appSecret;	// 接入凭证 AppSecret（HMAC-SHA256 签名密钥）
	private String username;	// 绑定的 xxl_job_user 账号
	private String callbackUrl;	// Webhook 结果回调默认URL，触发时可覆盖
	private int status;			// 状态：0-禁用、1-启用
	private String remark;		// 备注
	private Date addTime;
	private Date updateTime;

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getAppKey() {
		return appKey;
	}

	public void setAppKey(String appKey) {
		this.appKey = appKey;
	}

	public String getAppSecret() {
		return appSecret;
	}

	public void setAppSecret(String appSecret) {
		this.appSecret = appSecret;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getCallbackUrl() {
		return callbackUrl;
	}

	public void setCallbackUrl(String callbackUrl) {
		this.callbackUrl = callbackUrl;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public Date getAddTime() {
		return addTime;
	}

	public void setAddTime(Date addTime) {
		this.addTime = addTime;
	}

	public Date getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(Date updateTime) {
		this.updateTime = updateTime;
	}

}
