package com.xxl.job.admin.openapi;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 可重复读取 body 的请求包装，供开放API签名校验时读取原始 body，
 * 之后 Spring 仍可正常通过 {@code @RequestBody} 读取。
 *
 * @author xxl-job-pro
 */
public class OpenApiRequestWrapper extends HttpServletRequestWrapper {

	private final byte[] body;

	public OpenApiRequestWrapper(HttpServletRequest request) throws IOException {
		super(request);
		this.body = readBody(request);
	}

	private static byte[] readBody(HttpServletRequest request) throws IOException {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		int len;
		try (ServletInputStream in = request.getInputStream()) {
			while ((len = in.read(buffer)) != -1) {
				out.write(buffer, 0, len);
			}
		}
		return out.toByteArray();
	}

	/**
	 * raw request body as UTF-8 string
	 *
	 * @return body string
	 */
	public String getBodyAsString() {
		return new String(body, StandardCharsets.UTF_8);
	}

	@Override
	public ServletInputStream getInputStream() {
		final ByteArrayInputStream bais = new ByteArrayInputStream(body);
		return new ServletInputStream() {
			@Override
			public boolean isFinished() {
				return bais.available() == 0;
			}

			@Override
			public boolean isReady() {
				return true;
			}

			@Override
			public void setReadListener(ReadListener readListener) {
				// not supported for cached body
			}

			@Override
			public int read() {
				return bais.read();
			}
		};
	}

	@Override
	public BufferedReader getReader() {
		return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
	}

}
