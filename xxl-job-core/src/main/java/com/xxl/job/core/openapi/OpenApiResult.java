package com.xxl.job.core.openapi;

/**
 * 开放API调用结果（对应服务端 ReturnT 结构）。
 *
 * @author xxl-job-pro
 */
public class OpenApiResult {

	private int code;
	private String msg;
	private Object content;

	public OpenApiResult() {
	}

	public OpenApiResult(int code, String msg, Object content) {
		this.code = code;
		this.msg = msg;
		this.content = content;
	}

	public boolean isSuccess() {
		return code == 200;
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public Object getContent() {
		return content;
	}

	public void setContent(Object content) {
		this.content = content;
	}

	/**
	 * content 的字符串形式（如新任务ID、logId）
	 *
	 * @return content string
	 */
	public String getContentAsString() {
		return content == null ? null : String.valueOf(content);
	}

	@Override
	public String toString() {
		return "OpenApiResult [code=" + code + ", msg=" + msg + ", content=" + content + "]";
	}

}
