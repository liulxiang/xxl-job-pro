package com.xxl.job.admin.openapi;

import com.xxl.job.admin.core.model.XxlJobApiClient;
import com.xxl.job.admin.core.model.XxlJobUser;
import com.xxl.job.admin.dao.XxlJobApiClientDao;
import com.xxl.job.admin.dao.XxlJobUserDao;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.util.GsonTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 开放API鉴权拦截器（AppKey + HMAC-SHA256 签名 + 时间戳 + nonce 防重放）。
 *
 * <p>校验通过后，将凭证绑定的 {@link XxlJobUser} 放入请求属性
 * {@link #OPENAPI_LOGIN_USER}，供 Controller 复用现有的 service 权限模型。</p>
 *
 * @author xxl-job-pro
 */
@Component
public class OpenApiAuthInterceptor implements HandlerInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(OpenApiAuthInterceptor.class);

	/**
	 * request attribute key, hold the bound login user
	 */
	public static final String OPENAPI_LOGIN_USER = "XXL_JOB_OPENAPI_LOGIN_USER";

	/**
	 * request attribute key, hold the resolved api client
	 */
	public static final String OPENAPI_CLIENT = "XXL_JOB_OPENAPI_CLIENT";

	@Resource
	private XxlJobApiClientDao xxlJobApiClientDao;
	@Resource
	private XxlJobUserDao xxlJobUserDao;
	@Resource
	private OpenApiNonceCache nonceCache;

	/**
	 * 时间戳有效窗口（毫秒），默认 5 分钟
	 */
	@Value("${xxl.job.openapi.timestamp.window:300000}")
	private long timestampWindow;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

		if (!(handler instanceof HandlerMethod)) {
			return true;
		}

		// 1、header params
		String appKey = request.getHeader(OpenApiSignUtil.HEADER_APP_KEY);
		String timestamp = request.getHeader(OpenApiSignUtil.HEADER_TIMESTAMP);
		String nonce = request.getHeader(OpenApiSignUtil.HEADER_NONCE);
		String sign = request.getHeader(OpenApiSignUtil.HEADER_SIGN);

		if (isBlank(appKey) || isBlank(timestamp) || isBlank(nonce) || isBlank(sign)) {
			return reject(response, "missing auth header (app-key/timestamp/nonce/sign).");
		}

		// 2、timestamp window (anti-replay)
		long ts;
		try {
			ts = Long.parseLong(timestamp.trim());
		} catch (NumberFormatException e) {
			return reject(response, "invalid timestamp.");
		}
		long now = System.currentTimeMillis();
		if (Math.abs(now - ts) > timestampWindow) {
			return reject(response, "request timestamp expired.");
		}

		// 3、load client
		XxlJobApiClient client = xxlJobApiClientDao.loadByAppKey(appKey);
		if (client == null) {
			return reject(response, "invalid app-key or client disabled.");
		}

		// 4、verify sign
		String rawBody = (request instanceof OpenApiRequestWrapper)
				? ((OpenApiRequestWrapper) request).getBodyAsString() : "";
		String stringToSign = OpenApiSignUtil.buildStringToSign(appKey, timestamp, nonce, rawBody);
		String expectSign = OpenApiSignUtil.hmacSha256Hex(client.getAppSecret(), stringToSign);
		if (!OpenApiSignUtil.secureEquals(expectSign, sign)) {
			logger.warn("openapi sign mismatch, appKey={}", appKey);
			return reject(response, "sign not match.");
		}

		// 5、nonce anti-replay
		if (!nonceCache.putIfAbsent(nonce, timestampWindow)) {
			return reject(response, "replay detected, nonce reused.");
		}

		// 6、bind user (permission model reuse)
		XxlJobUser loginUser = xxlJobUserDao.loadByUserName(client.getUsername());
		if (loginUser == null) {
			return reject(response, "bound user not found for client.");
		}
		request.setAttribute(OPENAPI_LOGIN_USER, loginUser);
		request.setAttribute(OPENAPI_CLIENT, client);

		return true;
	}

	private boolean isBlank(String str) {
		return str == null || str.trim().length() == 0;
	}

	private boolean reject(HttpServletResponse response, String msg) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType("application/json;charset=UTF-8");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		ReturnT<String> result = new ReturnT<String>(HttpServletResponse.SC_UNAUTHORIZED, msg);
		response.getWriter().write(GsonTool.toJson(result));
		response.getWriter().flush();
		return false;
	}

}
