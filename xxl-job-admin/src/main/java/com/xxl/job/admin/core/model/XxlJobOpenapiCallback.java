package com.xxl.job.admin.core.model;

import java.util.Date;

/**
 * 开放API Webhook 回调登记：触发任务时按 logId 登记，任务完成时消费并回调。
 *
 * @author xxl-job-pro
 */
public class XxlJobOpenapiCallback {

	/** 待回调 */
	public static final int STATUS_PENDING = 0;
	/** 已回调 */
	public static final int STATUS_DONE = 1;
	/** 已放弃（重试超限或无法投递） */
	public static final int STATUS_ABANDONED = 2;

	private long id;
	private long logId;		// 触发产生的调度日志ID
	private String appKey;	// 发起触发的AppKey
	private String webhookUrl;	// 结果回调URL
	private int status;		// 0-待回调、1-已回调、2-已放弃
	private int retryCount;	// 已重试次数
	private Date nextRetryTime;	// 下次重试/补偿时间
	private Date addTime;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public long getLogId() {
		return logId;
	}

	public void setLogId(long logId) {
		this.logId = logId;
	}

	public String getAppKey() {
		return appKey;
	}

	public void setAppKey(String appKey) {
		this.appKey = appKey;
	}

	public String getWebhookUrl() {
		return webhookUrl;
	}

	public void setWebhookUrl(String webhookUrl) {
		this.webhookUrl = webhookUrl;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}

	public Date getNextRetryTime() {
		return nextRetryTime;
	}

	public void setNextRetryTime(Date nextRetryTime) {
		this.nextRetryTime = nextRetryTime;
	}

	public Date getAddTime() {
		return addTime;
	}

	public void setAddTime(Date addTime) {
		this.addTime = addTime;
	}

}
