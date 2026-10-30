package com.xxl.job.admin.openapi;

import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * 仅对开放API路径（/openapi/**）包装请求，缓存 body 以支持签名校验时的重复读取。
 *
 * @author xxl-job-pro
 */
@Component
public class OpenApiRequestFilter implements Filter {

	private static final String OPENAPI_PATH_PREFIX = "/openapi";

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		if (request instanceof HttpServletRequest) {
			HttpServletRequest httpRequest = (HttpServletRequest) request;
			String uri = httpRequest.getRequestURI();
			String contextPath = httpRequest.getContextPath() == null ? "" : httpRequest.getContextPath();
			String path = uri.substring(contextPath.length());
			if (path.startsWith(OPENAPI_PATH_PREFIX)) {
				chain.doFilter(new OpenApiRequestWrapper(httpRequest), response);
				return;
			}
		}
		chain.doFilter(request, response);
	}

}
