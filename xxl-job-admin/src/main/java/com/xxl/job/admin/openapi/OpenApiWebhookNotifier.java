package com.xxl.job.admin.openapi;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobApiClient;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.model.XxlJobOpenapiCallback;
import com.xxl.job.core.util.GsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 开放API Webhook 回调发送器：任务完成时，向登记的 webhook_url 异步 POST 执行结果。
 *
 * <p>由 {@link com.xxl.job.admin.core.complete.XxlJobCompleter} 在任务结束时调用。
 * 采用独立守护线程池，避免阻塞调度完成链路；回调成功（HTTP 2xx）后标记为已回调。</p>
 *
 * @author xxl-job-pro
 */
public class OpenApiWebhookNotifier {

	private static final Logger logger = LoggerFactory.getLogger(OpenApiWebhookNotifier.class);

	private static final ExecutorService executor = Executors.newFixedThreadPool(
			Runtime.getRuntime().availableProcessors(),
			new ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					Thread t = new Thread(r, "xxl-job, admin OpenApiWebhookNotifier-" + r.hashCode());
					t.setDaemon(true);
					return t;
				}
			});

	private OpenApiWebhookNotifier() {
	}

	/**
	 * fire webhook callback (async)
	 *
	 * @param callback  登记信息
	 * @param xxlJobLog 完成的日志
	 */
	public static void notifyAsync(final XxlJobOpenapiCallback callback, final XxlJobLog xxlJobLog) {
		if (callback == null || callback.getWebhookUrl() == null || callback.getWebhookUrl().trim().length() == 0) {
			return;
		}
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					if (doNotify(callback, xxlJobLog)) {
						XxlJobAdminConfig.getAdminConfig().getXxlJobOpenapiCallbackDao()
								.updateStatus(callback.getLogId(), XxlJobOpenapiCallback.STATUS_PENDING, XxlJobOpenapiCallback.STATUS_DONE);
						}
					} catch (Exception e) {
						logger.error(">>>>>>>>>>> xxl-job, openapi webhook error, logId=" + callback.getLogId() + ", url=" + callback.getWebhookUrl(), e);
					}
				}
			});
		}

	/**
	 * synchronous notify, return whether delivered (HTTP 2xx). status is managed by caller.
	 *
	 * @param callback  登记信息
	 * @param xxlJobLog 完成的日志
	 * @return delivered or not
	 */
	public static boolean notifySync(XxlJobOpenapiCallback callback, XxlJobLog xxlJobLog) {
		if (callback == null || callback.getWebhookUrl() == null || callback.getWebhookUrl().trim().length() == 0) {
			return false;
		}
		try {
			return doNotify(callback, xxlJobLog);
		} catch (Exception e) {
			logger.error(">>>>>>>>>>> xxl-job, openapi webhook(sync) error, logId=" + callback.getLogId() + ", url=" + callback.getWebhookUrl(), e);
			return false;
		}
	}

	private static boolean doNotify(XxlJobOpenapiCallback callback, XxlJobLog xxlJobLog) throws Exception {
		String payload = buildPayload(xxlJobLog);

		// 回调来源验签：用发起方的 AppSecret 对 payload 做 HMAC-SHA256，接收方可重算校验
		String sign = buildCallbackSign(callback.getAppKey(), payload);

		int httpCode = post(callback.getWebhookUrl().trim(), payload, sign);
		if (httpCode >= 200 && httpCode < 300) {
			logger.debug(">>>>>>>>>>> xxl-job, openapi webhook success, logId={}, url={}", callback.getLogId(), callback.getWebhookUrl());
			return true;
		}
		logger.warn(">>>>>>>>>>> xxl-job, openapi webhook fail(non-2xx), logId={}, url={}, httpCode={}", callback.getLogId(), callback.getWebhookUrl(), httpCode);
		return false;
	}

	/**
	 * 计算回调验签：lowercaseHex( HmacSHA256( appSecret, payload ) )。
	 * 客户端被禁用/删除导致取不到 secret 时返回 null（不携带头），接收方应将无签名视为不可信。
	 */
	private static String buildCallbackSign(String appKey, String payload) {
		if (appKey == null || appKey.trim().length() == 0) {
			return null;
		}
		try {
			XxlJobApiClient client = XxlJobAdminConfig.getAdminConfig().getXxlJobApiClientDao().loadByAppKey(appKey);
			if (client == null || client.getAppSecret() == null) {
				logger.warn(">>>>>>>>>>> xxl-job, openapi webhook sign skipped, client not found or disabled, appKey={}", appKey);
				return null;
			}
			return OpenApiSignUtil.hmacSha256Hex(client.getAppSecret(), payload);
		} catch (Exception e) {
			logger.error(">>>>>>>>>>> xxl-job, openapi webhook sign error, appKey=" + appKey, e);
			return null;
		}
	}

	private static String buildPayload(XxlJobLog log) {
		Map<String, Object> data = new LinkedHashMap<String, Object>();
		data.put("logId", log.getId());
		data.put("jobId", log.getJobId());
		data.put("jobGroup", log.getJobGroup());
		data.put("executorAddress", log.getExecutorAddress());
		data.put("triggerCode", log.getTriggerCode());
		data.put("handleCode", log.getHandleCode());
		data.put("handleMsg", stripHtml(log.getHandleMsg()));
		data.put("finishTime", System.currentTimeMillis());
		return GsonTool.toJson(data);
	}

	private static String stripHtml(String msg) {
		if (msg == null) {
			return null;
		}
		String plain = msg.replaceAll("<br>", "\n").replaceAll("<[^>]+>", "");
		if (plain.length() > 2000) {
			plain = plain.substring(0, 2000);
		}
		return plain;
	}

	private static int post(String urlStr, String payload, String sign) throws Exception {
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) new URL(urlStr).openConnection();
			conn.setRequestMethod("POST");
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(10000);
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
			if (sign != null) {
				conn.setRequestProperty(OpenApiSignUtil.HEADER_CALLBACK_SIGN, sign);
			}
			byte[] out = payload.getBytes(StandardCharsets.UTF_8);
			conn.setRequestProperty("Content-Length", String.valueOf(out.length));
			try (OutputStream os = conn.getOutputStream()) {
				os.write(out);
				os.flush();
			}
			int code = conn.getResponseCode();
			// drain
			InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
			drain(is);
			return code;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	private static void drain(InputStream is) {
		if (is == null) {
			return;
		}
		try (InputStream in = is) {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buffer = new byte[2048];
			int len;
			while ((len = in.read(buffer)) != -1) {
				bos.write(buffer, 0, len);
			}
		} catch (Exception e) {
			// ignore
		}
	}

}
