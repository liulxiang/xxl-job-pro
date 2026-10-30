package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.model.XxlJobOpenapiCallback;
import com.xxl.job.admin.dao.XxlJobOpenapiCallbackDao;
import com.xxl.job.admin.openapi.OpenApiWebhookNotifier;
import com.xxl.job.core.biz.model.ReturnT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 开放API Webhook 失败补偿重投 Helper。
 *
 * <p>周期性扫描 {@code xxl_job_openapi_callback} 中处于「待回调」且已到重试时间的记录：
 * 若对应任务已进入终态则重投；投递失败按退避推迟下次重试；超过上限则放弃。
 * 通过 next_retry_time 作为乐观锁 token 认领，保证集群下不重复投递。</p>
 *
 * @author xxl-job-pro
 */
public class JobWebhookRetryHelper {
	private static Logger logger = LoggerFactory.getLogger(JobWebhookRetryHelper.class);

	private static JobWebhookRetryHelper instance = new JobWebhookRetryHelper();
	public static JobWebhookRetryHelper getInstance() {
		return instance;
	}

	/** 最大重试次数 */
	private static final int MAX_RETRY = 5;
	/** 投递失败后的退避（毫秒） */
	private static final long RETRY_BACKOFF_MS = 120_000L;
	/** 任务未完成时的顺延（毫秒），不计入重试次数 */
	private static final long NOT_FINISHED_DEFER_MS = 120_000L;
	/** 每轮批量 */
	private static final int PAGESIZE = 50;

	private Thread monitorThread;
	private volatile boolean toStop = false;

	public void start() {
		monitorThread = new Thread(new Runnable() {
			@Override
			public void run() {

				while (!toStop) {
					try {
						XxlJobOpenapiCallbackDao callbackDao = XxlJobAdminConfig.getAdminConfig().getXxlJobOpenapiCallbackDao();

						List<XxlJobOpenapiCallback> pendings = callbackDao.findPending(new Date(), MAX_RETRY, PAGESIZE);
						if (pendings != null && !pendings.isEmpty()) {
							long now = System.currentTimeMillis();
							for (XxlJobOpenapiCallback cb : pendings) {
								processOne(cb, now);
							}
						}
					} catch (Throwable e) {
						if (!toStop) {
							logger.error(">>>>>>>>>>> xxl-job, job webhook retry monitor thread error:{}", e);
						}
					}

					try {
						TimeUnit.SECONDS.sleep(30);
					} catch (Throwable e) {
						if (!toStop) {
							logger.error(e.getMessage(), e);
						}
					}
				}

				logger.info(">>>>>>>>>>> xxl-job, job webhook retry monitor thread stop");
			}
		});
		monitorThread.setDaemon(true);
		monitorThread.setName("xxl-job, admin JobWebhookRetryHelper");
		monitorThread.start();
	}

	private void processOne(XxlJobOpenapiCallback cb, long now) {
		XxlJobOpenapiCallbackDao callbackDao = XxlJobAdminConfig.getAdminConfig().getXxlJobOpenapiCallbackDao();

		// 1、load log, 判断是否终态
		XxlJobLog log = XxlJobAdminConfig.getAdminConfig().getXxlJobLogDao().load(cb.getLogId());
		if (log == null) {
			// 日志已清理，无法投递，放弃
			callbackDao.updateStatus(cb.getLogId(), XxlJobOpenapiCallback.STATUS_PENDING, XxlJobOpenapiCallback.STATUS_ABANDONED);
			return;
		}
		if (!isFinished(log)) {
			// 任务仍在运行，顺延（不增加重试次数），避免过早投递
			callbackDao.deferNext(cb.getLogId(), new Date(now + NOT_FINISHED_DEFER_MS));
			return;
		}

		// 2、乐观认领：以 next_retry_time 为 token，claim 成功者才投递（集群去重）
		Date newNext = new Date(now + RETRY_BACKOFF_MS);
		int claimed = callbackDao.claim(cb.getLogId(), cb.getNextRetryTime(), newNext);
		if (claimed < 1) {
			return;   // 被其它节点/线程认领，跳过
		}

		// 3、投递
		boolean delivered = OpenApiWebhookNotifier.notifySync(cb, log);
		if (delivered) {
			callbackDao.updateStatus(cb.getLogId(), XxlJobOpenapiCallback.STATUS_PENDING, XxlJobOpenapiCallback.STATUS_DONE);
			return;
		}

		// 4、失败：claim 已使 retry_count+1，达上限则放弃，否则按退避于 next_retry_time 重投
		if (cb.getRetryCount() + 1 >= MAX_RETRY) {
			callbackDao.updateStatus(cb.getLogId(), XxlJobOpenapiCallback.STATUS_PENDING, XxlJobOpenapiCallback.STATUS_ABANDONED);
			logger.warn(">>>>>>>>>>> xxl-job, openapi webhook abandoned after {} retries, logId={}, url={}", MAX_RETRY, cb.getLogId(), cb.getWebhookUrl());
		}
		// 未达上限时，claim 已将 next_retry_time 设为退避时间，等待下轮
	}

	/**
	 * 是否终态：执行结果已回填，或调度阶段失败（无执行器可投递）
	 */
	private boolean isFinished(XxlJobLog log) {
		if (log.getHandleCode() != 0) {
			return true;
		}
		int triggerCode = log.getTriggerCode();
		return triggerCode != 0 && triggerCode != ReturnT.SUCCESS_CODE;
	}

	public void toStop() {
		toStop = true;
		if (monitorThread != null) {
			monitorThread.interrupt();
			try {
				monitorThread.join();
			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

}
