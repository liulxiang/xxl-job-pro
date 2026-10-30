package com.xxl.job.core.openapi;

import com.xxl.job.core.util.GsonTool;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 开放API客户端SDK：封装签名/时间戳/nonce，供多个项目直接依赖 xxl-job-core 调用调度中心。
 *
 * <p>签名约定与服务端 {@code OpenApiSignUtil} 完全一致：</p>
 * <pre>
 * stringToSign = appKey + "\n" + timestamp + "\n" + nonce + "\n" + rawBody
 * sign         = lowercaseHex( HmacSHA256( appSecret, stringToSign ) )
 * </pre>
 *
 * <p>线程安全，可复用单例。内部基于 JDK HttpURLConnection，无额外依赖。</p>
 *
 * @author xxl-job-pro
 */
public class OpenApiClient {

	private static final String HEADER_APP_KEY = "XXL-JOB-APP-KEY";
	private static final String HEADER_TIMESTAMP = "XXL-JOB-TIMESTAMP";
	private static final String HEADER_NONCE = "XXL-JOB-NONCE";
	private static final String HEADER_SIGN = "XXL-JOB-SIGN";

	private final String baseUrl;
	private final String appKey;
	private final String appSecret;
	private int connectTimeout = 5000;
	private int readTimeout = 10000;

	/**
	 * @param baseUrl    调度中心地址，如 http://127.0.0.1:8080/xxl-job-admin
	 * @param appKey     分配的 AppKey
	 * @param appSecret  分配的 AppSecret
	 */
	public OpenApiClient(String baseUrl, String appKey, String appSecret) {
		if (baseUrl == null || baseUrl.trim().length() == 0 || appKey == null || appSecret == null) {
			throw new IllegalArgumentException("baseUrl/appKey/appSecret can not be empty.");
		}
		// trim trailing slash
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.appKey = appKey;
		this.appSecret = appSecret;
	}

	public void setTimeout(int connectTimeout, int readTimeout) {
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
	}

	// ---------------------- job manage ----------------------

	/**
	 * 创建任务
	 *
	 * @param jobInfo 任务字段（Map，key 与服务端 XxlJobInfo 字段一致，如 jobGroup/jobDesc/executorHandler...）
	 * @return result, content = 新任务ID
	 */
	public OpenApiResult addJob(Map<String, Object> jobInfo) {
		return post("/openapi/v1/job/add", GsonTool.toJson(jobInfo));
	}

	/**
	 * 更新任务（jobInfo 需包含 id）
	 */
	public OpenApiResult updateJob(Map<String, Object> jobInfo) {
		return post("/openapi/v1/job/update", GsonTool.toJson(jobInfo));
	}

	public OpenApiResult startJob(int jobId) {
		return post("/openapi/v1/job/start?id=" + jobId, "");
	}

	public OpenApiResult stopJob(int jobId) {
		return post("/openapi/v1/job/stop?id=" + jobId, "");
	}

	public OpenApiResult removeJob(int jobId) {
		return post("/openapi/v1/job/remove?id=" + jobId, "");
	}

	/**
	 * 查询任务详情，content 为任务对象（Map）
	 */
	public OpenApiResult getJob(int jobId) {
		return post("/openapi/v1/job/get?id=" + jobId, "");
	}

	// ---------------------- trigger ----------------------

	/**
	 * 触发任务
	 *
	 * @param jobId          任务ID
	 * @param executorParam  执行参数，null 则使用任务默认参数
	 * @param addressList    指定执行器地址，可为 null
	 * @return result, content = 本次调度的 logId
	 */
	public OpenApiResult trigger(int jobId, String executorParam, String addressList) {
		return trigger(jobId, executorParam, addressList, null);
	}

	/**
	 * 触发任务（带本次回调URL，优先于客户端默认回调地址）
	 *
	 * @param webhookUrl 本次触发的结果回调URL，可为 null（回退到服务端凭证默认回调URL）
	 */
	public OpenApiResult trigger(int jobId, String executorParam, String addressList, String webhookUrl) {
		Map<String, Object> body = new HashMap<String, Object>();
		body.put("jobId", jobId);
		body.put("executorParam", executorParam);
		body.put("addressList", addressList);
		if (webhookUrl != null) {
			body.put("webhookUrl", webhookUrl);
		}
		return post("/openapi/v1/job/trigger", GsonTool.toJson(body));
	}

	/**
	 * 回查执行结果，content 为结果 Map（triggerCode/handleCode/handleMsg...）
	 *
	 * @param logId 触发返回的 logId
	 * @return result
	 */
	public OpenApiResult queryResult(long logId) {
		return post("/openapi/v1/joblog/result?logId=" + logId, "");
	}

	// ---------------------- core invoke ----------------------

	private OpenApiResult post(String path, String body) {
		String rawBody = body == null ? "" : body;
		String timestamp = String.valueOf(System.currentTimeMillis());
		String nonce = UUID.randomUUID().toString().replace("-", "");
		String sign = sign(appSecret, buildStringToSign(appKey, timestamp, nonce, rawBody));

		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) new URL(baseUrl + path).openConnection();
			conn.setRequestMethod("POST");
			conn.setConnectTimeout(connectTimeout);
			conn.setReadTimeout(readTimeout);
			conn.setDoOutput(true);
			conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
			conn.setRequestProperty(HEADER_APP_KEY, appKey);
			conn.setRequestProperty(HEADER_TIMESTAMP, timestamp);
			conn.setRequestProperty(HEADER_NONCE, nonce);
			conn.setRequestProperty(HEADER_SIGN, sign);

			byte[] out = rawBody.getBytes(StandardCharsets.UTF_8);
			conn.setRequestProperty("Content-Length", String.valueOf(out.length));
			try (OutputStream os = conn.getOutputStream()) {
				os.write(out);
				os.flush();
			}

			int status = conn.getResponseCode();
			InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
			String resp = read(is);

			OpenApiResult result = parse(resp);
			if (result == null) {
				return new OpenApiResult(status, "empty or invalid response: " + resp, null);
			}
			return result;
		} catch (IOException e) {
			return new OpenApiResult(500, "openapi request error: " + e.getMessage(), null);
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	private OpenApiResult parse(String resp) {
		if (resp == null || resp.trim().length() == 0) {
			return null;
		}
		try {
			return GsonTool.fromJson(resp, OpenApiResult.class);
		} catch (Exception e) {
			return null;
		}
	}

	private static String read(InputStream is) throws IOException {
		if (is == null) {
			return "";
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int len;
		try (InputStream in = is) {
			while ((len = in.read(buffer)) != -1) {
				out.write(buffer, 0, len);
			}
		}
		return new String(out.toByteArray(), StandardCharsets.UTF_8);
	}

	// ---------------------- sign ----------------------

	static String buildStringToSign(String appKey, String timestamp, String nonce, String rawBody) {
		return appKey + "\n" + timestamp + "\n" + nonce + "\n" + (rawBody == null ? "" : rawBody);
	}

	static String sign(String appSecret, String data) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return toHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new RuntimeException("openapi client sign error: " + e.getMessage(), e);
		}
	}

	private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

	private static String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(HEX_CHARS[(b >> 4) & 0x0F]).append(HEX_CHARS[b & 0x0F]);
		}
		return sb.toString();
	}

}
