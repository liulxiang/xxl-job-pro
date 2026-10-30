package com.xxl.job.admin.controller;

import com.xxl.job.admin.controller.annotation.PermissionLimit;
import com.xxl.job.admin.core.model.XxlJobApiClient;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLog;
import com.xxl.job.admin.core.model.XxlJobOpenapiCallback;
import com.xxl.job.admin.core.model.XxlJobUser;
import com.xxl.job.admin.core.trigger.TriggerTypeEnum;
import com.xxl.job.admin.core.trigger.XxlJobTrigger;
import com.xxl.job.admin.dao.XxlJobInfoDao;
import com.xxl.job.admin.dao.XxlJobLogDao;
import com.xxl.job.admin.dao.XxlJobOpenapiCallbackDao;
import com.xxl.job.admin.openapi.OpenApiAuthInterceptor;
import com.xxl.job.admin.service.XxlJobService;
import com.xxl.job.core.biz.model.ReturnT;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放API：面向第三方系统（异构项目）的任务管理与触发。
 *
 * <p>鉴权由 {@link com.xxl.job.admin.openapi.OpenApiAuthInterceptor} 完成（AppKey + HMAC-SHA256
 * 签名 + 时间戳 + nonce 防重放），校验通过后绑定的 {@link XxlJobUser} 会被放入请求属性，
 * 这里复用后台 service 的权限模型（执行器组粒度）。</p>
 *
 * <p>所有路径以 {@code /openapi/v1} 为前缀，与内部 {@code /api}（调度中心↔执行器协议）
 * 以及后台 {@code /jobinfo}（Cookie 会话）物理隔离。</p>
 *
 * @author xxl-job-pro
 */
@RestController
@RequestMapping("/openapi/v1")
public class JobOpenApiController {

	@Resource
	private XxlJobService xxlJobService;
	@Resource
	private XxlJobInfoDao xxlJobInfoDao;
	@Resource
	private XxlJobLogDao xxlJobLogDao;
	@Resource
	private XxlJobOpenapiCallbackDao xxlJobOpenapiCallbackDao;

	// -------------------- job manage --------------------

	/**
	 * 创建任务，返回新任务ID
	 */
	@RequestMapping("/job/add")
	@PermissionLimit(limit = false)
	public ReturnT<String> add(HttpServletRequest request, @RequestBody XxlJobInfo jobInfo) {
		XxlJobUser loginUser = getOpenApiUser(request);
		// jobGroup permission (service.add 不校验组权限，这里补齐)
		ReturnT<String> perm = checkGroupPermission(loginUser, jobInfo.getJobGroup());
		if (perm != null) {
			return perm;
		}
		return xxlJobService.add(jobInfo, loginUser);
	}

	/**
	 * 更新任务
	 */
	@RequestMapping("/job/update")
	@PermissionLimit(limit = false)
	public ReturnT<String> update(HttpServletRequest request, @RequestBody XxlJobInfo jobInfo) {
		XxlJobUser loginUser = getOpenApiUser(request);
		ReturnT<String> perm = checkJobPermission(loginUser, jobInfo.getId());
		if (perm != null) {
			return perm;
		}
		return xxlJobService.update(jobInfo, loginUser);
	}

	/**
	 * 启动任务
	 */
	@RequestMapping("/job/start")
	@PermissionLimit(limit = false)
	public ReturnT<String> start(HttpServletRequest request, @RequestParam int id) {
		XxlJobUser loginUser = getOpenApiUser(request);
		ReturnT<String> perm = checkJobPermission(loginUser, id);
		if (perm != null) {
			return perm;
		}
		return xxlJobService.start(id);
	}

	/**
	 * 停止任务
	 */
	@RequestMapping("/job/stop")
	@PermissionLimit(limit = false)
	public ReturnT<String> stop(HttpServletRequest request, @RequestParam int id) {
		XxlJobUser loginUser = getOpenApiUser(request);
		ReturnT<String> perm = checkJobPermission(loginUser, id);
		if (perm != null) {
			return perm;
		}
		return xxlJobService.stop(id);
	}

	/**
	 * 删除任务
	 */
	@RequestMapping("/job/remove")
	@PermissionLimit(limit = false)
	public ReturnT<String> remove(HttpServletRequest request, @RequestParam int id) {
		XxlJobUser loginUser = getOpenApiUser(request);
		ReturnT<String> perm = checkJobPermission(loginUser, id);
		if (perm != null) {
			return perm;
		}
		return xxlJobService.remove(id);
	}

	/**
	 * 查询任务详情
	 */
	@RequestMapping("/job/get")
	@PermissionLimit(limit = false)
	public ReturnT<XxlJobInfo> get(HttpServletRequest request, @RequestParam int id) {
		XxlJobUser loginUser = getOpenApiUser(request);
		XxlJobInfo jobInfo = xxlJobInfoDao.loadById(id);
		if (jobInfo == null) {
			return new ReturnT<XxlJobInfo>(ReturnT.FAIL_CODE, "job not found, id=" + id);
		}
		if (!loginUser.validPermission(jobInfo.getJobGroup())) {
			return new ReturnT<XxlJobInfo>(ReturnT.FAIL_CODE, "permission limit for jobGroup=" + jobInfo.getJobGroup());
		}
		return new ReturnT<XxlJobInfo>(jobInfo);
	}

	// -------------------- trigger --------------------

	/**
	 * 触发任务（手动/事件），同步返回本次调度的 logId，便于调用方回查执行结果。
	 */
	@RequestMapping("/job/trigger")
	@PermissionLimit(limit = false)
	public ReturnT<String> trigger(HttpServletRequest request, @RequestBody TriggerRequest req) {
		XxlJobUser loginUser = getOpenApiUser(request);

		XxlJobInfo jobInfo = xxlJobInfoDao.loadById(req.getJobId());
		if (jobInfo == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "job not found, id=" + req.getJobId());
		}
		if (!loginUser.validPermission(jobInfo.getJobGroup())) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "permission limit for jobGroup=" + jobInfo.getJobGroup());
		}

		// force cover job param
		String executorParam = req.getExecutorParam() == null ? "" : req.getExecutorParam();

		// 同步触发，拿到 logId（executor.run 为快速投递，实际执行异步）
		List<Long> logIds = XxlJobTrigger.trigger(req.getJobId(), TriggerTypeEnum.API, -1, null, executorParam, req.getAddressList());
		if (logIds.isEmpty()) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "trigger fail, no log generated");
		}
		long logId = logIds.get(0);

		// webhook：本次触发优先使用请求传入的 URL，其次回退到客户端默认回调URL
		String webhookUrl = StringUtils.hasText(req.getWebhookUrl()) ? req.getWebhookUrl().trim() : getClientDefaultCallbackUrl(request);
		if (StringUtils.hasText(webhookUrl)) {
			Date now = new Date();
			XxlJobOpenapiCallback callback = new XxlJobOpenapiCallback();
			callback.setLogId(logId);
			callback.setAppKey(getOpenApiClientAppKey(request));
			callback.setWebhookUrl(webhookUrl);
			callback.setStatus(XxlJobOpenapiCallback.STATUS_PENDING);
			callback.setRetryCount(0);
			// 首次补偿宽限期 2 分钟，正常完成链路的异步回调会在此之前完成
			callback.setNextRetryTime(new Date(now.getTime() + 120_000L));
			callback.setAddTime(now);
			xxlJobOpenapiCallbackDao.save(callback);
		}

		return new ReturnT<String>(String.valueOf(logId));
	}

	/**
	 * 回查任务执行结果（调度结果 + 执行结果）
	 */
	@RequestMapping("/joblog/result")
	@PermissionLimit(limit = false)
	public ReturnT<Map<String, Object>> result(HttpServletRequest request, @RequestParam long logId) {
		XxlJobUser loginUser = getOpenApiUser(request);

		XxlJobLog jobLog = xxlJobLogDao.load(logId);
		if (jobLog == null) {
			return new ReturnT<Map<String, Object>>(ReturnT.FAIL_CODE, "log not found, logId=" + logId);
		}
		if (!loginUser.validPermission(jobLog.getJobGroup())) {
			return new ReturnT<Map<String, Object>>(ReturnT.FAIL_CODE, "permission limit for jobGroup=" + jobLog.getJobGroup());
		}

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("logId", jobLog.getId());
		data.put("jobId", jobLog.getJobId());
		data.put("triggerCode", jobLog.getTriggerCode());
		data.put("triggerMsg", jobLog.getTriggerMsg());
		data.put("handleCode", jobLog.getHandleCode());
		data.put("handleMsg", jobLog.getHandleMsg());
		return new ReturnT<Map<String, Object>>(data);
	}

	// -------------------- helper --------------------

	private XxlJobUser getOpenApiUser(HttpServletRequest request) {
		return (XxlJobUser) request.getAttribute(OpenApiAuthInterceptor.OPENAPI_LOGIN_USER);
	}

	private XxlJobApiClient getOpenApiClient(HttpServletRequest request) {
		return (XxlJobApiClient) request.getAttribute(OpenApiAuthInterceptor.OPENAPI_CLIENT);
	}

	private String getOpenApiClientAppKey(HttpServletRequest request) {
		XxlJobApiClient client = getOpenApiClient(request);
		return client == null ? null : client.getAppKey();
	}

	private String getClientDefaultCallbackUrl(HttpServletRequest request) {
		XxlJobApiClient client = getOpenApiClient(request);
		return client == null ? null : client.getCallbackUrl();
	}

	private ReturnT<String> checkGroupPermission(XxlJobUser loginUser, int jobGroup) {
		if (loginUser == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "openapi user not resolved");
		}
		if (!loginUser.validPermission(jobGroup)) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "permission limit for jobGroup=" + jobGroup);
		}
		return null;
	}

	private ReturnT<String> checkJobPermission(XxlJobUser loginUser, int jobId) {
		XxlJobInfo jobInfo = xxlJobInfoDao.loadById(jobId);
		if (jobInfo == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, "job not found, id=" + jobId);
		}
		return checkGroupPermission(loginUser, jobInfo.getJobGroup());
	}

	// -------------------- request dto --------------------

	/**
	 * trigger 请求体
	 */
	public static class TriggerRequest {
		private int jobId;
		private String executorParam;
		private String addressList;
		private String webhookUrl;

		public int getJobId() {
			return jobId;
		}

		public void setJobId(int jobId) {
			this.jobId = jobId;
		}

		public String getExecutorParam() {
			return executorParam;
		}

		public void setExecutorParam(String executorParam) {
			this.executorParam = executorParam;
		}

		public String getAddressList() {
			return addressList;
		}

		public void setAddressList(String addressList) {
			this.addressList = addressList;
		}

		public String getWebhookUrl() {
			return webhookUrl;
		}

		public void setWebhookUrl(String webhookUrl) {
			this.webhookUrl = webhookUrl;
		}
	}

}
