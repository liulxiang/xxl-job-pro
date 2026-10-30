package com.xxl.job.executor.openapi;

import com.xxl.job.core.openapi.OpenApiClient;
import com.xxl.job.core.openapi.OpenApiResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开放API SDK（{@link OpenApiClient}）最小可运行示例。
 *
 * <p>演示「创建任务 → 启动 → 触发（拿 logId）→ 回查结果」的完整闭环。
 * 集成方仅需依赖 xxl-job-core，用后台「凭证管理」页面分配的 AppKey/AppSecret 即可。</p>
 *
 * <p>运行前修改 BASE_URL / APP_KEY / APP_SECRET / JOB_GROUP，并保证调度中心已启动、
 * 对应执行器组在线。直接 run 本类 main 方法即可。</p>
 *
 * @author xxl-job-pro
 */
public class OpenApiClientDemo {

	// ---- 按需修改 ----
	private static final String BASE_URL   = "http://127.0.0.1:8080/xxl-job-admin";
	private static final String APP_KEY    = "demo_app_key";
	private static final String APP_SECRET = "demo_app_secret_please_change_me";
	private static final int    JOB_GROUP  = 1;          // 执行器组ID（凭证需有该组权限）

	private static final OpenApiClient client = new OpenApiClient(BASE_URL, APP_KEY, APP_SECRET);

	public static void main(String[] args) throws Exception {

		// ========== 1、创建任务 ==========
		// 字段与服务端 XxlJobInfo 一致；下面等价于这样一份 JSON body：
		// {
		//   "jobGroup": 1,
		//   "jobDesc": "SDK创建的示例任务",
		//   "author": "openapi-demo",
		//   "alarmEmail": "",
		//   "scheduleType": "CRON",
		//   "scheduleConf": "0 0/5 * * * ?",
		//   "misfireStrategy": "DO_NOTHING",
		//   "executorRouteStrategy": "FIRST",
		//   "executorHandler": "demoJobHandler",
		//   "executorParam": "hello from openapi",
		//   "executorBlockStrategy": "SERIAL_EXECUTION",
		//   "executorTimeout": 0,
		//   "executorFailRetryCount": 0,
		//   "glueType": "BEAN"
		// }
		Map<String, Object> jobInfo = buildDemoJob();
		OpenApiResult addResult = client.addJob(jobInfo);
		System.out.println("[add]  " + addResult);
		if (!addResult.isSuccess()) {
			System.err.println("创建任务失败，示例终止。");
			return;
		}
		int jobId = Integer.parseInt(addResult.getContentAsString());   // content = 新任务ID
		System.out.println(">>> 新任务ID = " + jobId);

		// ========== 2、启动任务（进入调度） ==========
		System.out.println("[start] " + client.startJob(jobId));

		// ========== 3、立即触发一次，拿回本次调度的 logId ==========
		// 参数1 jobId；参数2 executorParam（覆盖任务默认参数，null 用默认）；
		// 参数3 addressList（指定执行器地址，null 走路由策略）；
		// 参数4 webhookUrl（本次结果回调地址，null 则回退凭证默认回调URL，可省略该重载）
		// 回调推送会附带请求头 Xxl-Job-Callback-Sign = lowercaseHex(HmacSHA256(appSecret, rawBody))，
		// 接收方可用同一 AppSecret 对收到的原始 body 重算 HMAC 比对，以校验回调来源可信。
		OpenApiResult triggerResult = client.trigger(jobId, "trigger at " + System.currentTimeMillis(), null);
		System.out.println("[trigger] " + triggerResult);
		if (!triggerResult.isSuccess()) {
			System.err.println("触发失败：" + triggerResult.getMsg());
			return;
		}
		long logId = Long.parseLong(triggerResult.getContentAsString());   // content = logId
		System.out.println(">>> 本次调度 logId = " + logId);

		// ========== 4、回查执行结果（轮询直到执行结束） ==========
		// 若配置了 webhookUrl，结果会由调度中心主动回调推送，无需轮询；
		// 未配置回调时，可用 logId 主动回查。
		Map<String, Object> result = pollResult(logId, 10);
		if (result != null) {
			System.out.println("[result] handleCode=" + result.get("handleCode")
					+ ", handleMsg=" + result.get("handleMsg"));
		} else {
			System.out.println("[result] 超时未拿到执行结果，可稍后再次回查 logId=" + logId);
		}

		// ========== 5、收尾（可选）：停止 / 删除 ==========
		// client.stopJob(jobId);
		// client.removeJob(jobId);
	}

	/**
	 * 轮询回查执行结果，直到 handleCode 非 0（执行结束）或超过最大次数。
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> pollResult(long logId, int maxTry) throws InterruptedException {
		for (int i = 0; i < maxTry; i++) {
			OpenApiResult r = client.queryResult(logId);
			if (r.isSuccess() && r.getContent() instanceof Map) {
				Map<String, Object> data = (Map<String, Object>) r.getContent();
				Object handleCode = data.get("handleCode");
				// handleCode：0-未执行/进行中；200-成功；其它-失败
				if (handleCode != null && !"0".equals(String.valueOf(handleCode))) {
					return data;
				}
			}
			Thread.sleep(1000);
		}
		return null;
	}

	private static Map<String, Object> buildDemoJob() {
		Map<String, Object> jobInfo = new LinkedHashMap<String, Object>();
		jobInfo.put("jobGroup", JOB_GROUP);
		jobInfo.put("jobDesc", "SDK创建的示例任务");
		jobInfo.put("author", "openapi-demo");
		jobInfo.put("alarmEmail", "");
		jobInfo.put("scheduleType", "CRON");                 // CRON / FIX_RATE
		jobInfo.put("scheduleConf", "0 0/5 * * * ?");        // CRON 表达式；FIX_RATE 时填秒数
		jobInfo.put("misfireStrategy", "DO_NOTHING");        // DO_NOTHING / FIRE_ONCE_NOW
		jobInfo.put("executorRouteStrategy", "FIRST");       // FIRST / LAST / ROUND / RANDOM / CONSISTENT_HASH ...
		jobInfo.put("executorHandler", "demoJobHandler");    // 执行器 @XxlJob("demoJobHandler")
		jobInfo.put("executorParam", "hello from openapi");
		jobInfo.put("executorBlockStrategy", "SERIAL_EXECUTION"); // SERIAL_EXECUTION / DISCARD_LATER / COVER_EARLY
		jobInfo.put("executorTimeout", 0);
		jobInfo.put("executorFailRetryCount", 0);
		jobInfo.put("glueType", "BEAN");                     // BEAN / GLUE_SHELL / GLUE_PYTHON ...
		return jobInfo;
	}

}
