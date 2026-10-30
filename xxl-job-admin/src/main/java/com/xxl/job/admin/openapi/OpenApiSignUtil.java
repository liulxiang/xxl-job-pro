package com.xxl.job.admin.openapi;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 开放API签名工具：HMAC-SHA256。
 *
 * <p>签名算法约定（客户端与服务端必须一致）：</p>
 * <pre>
 * stringToSign = appKey + "\n" + timestamp + "\n" + nonce + "\n" + rawBody
 * sign         = lowercaseHex( HmacSHA256( appSecret, stringToSign ) )
 * </pre>
 *
 * @author xxl-job-pro
 */
public class OpenApiSignUtil {

	// -------------------- header names --------------------
	public static final String HEADER_APP_KEY = "XXL-JOB-APP-KEY";
	public static final String HEADER_TIMESTAMP = "XXL-JOB-TIMESTAMP";
	public static final String HEADER_NONCE = "XXL-JOB-NONCE";
	public static final String HEADER_SIGN = "XXL-JOB-SIGN";
	/** Webhook 回调来源验签头：接收方可用 AppSecret 对 payload 重算 HMAC 校验 */
	public static final String HEADER_CALLBACK_SIGN = "Xxl-Job-Callback-Sign";

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	private OpenApiSignUtil() {
	}

	/**
	 * build the string to sign
	 *
	 * @param appKey    appKey
	 * @param timestamp timestamp (ms, string)
	 * @param nonce     nonce
	 * @param rawBody   raw request body, nullable
	 * @return string to sign
	 */
	public static String buildStringToSign(String appKey, String timestamp, String nonce, String rawBody) {
		return appKey + "\n" + timestamp + "\n" + nonce + "\n" + (rawBody == null ? "" : rawBody);
	}

	/**
	 * hmac-sha256 sign, lowercase hex output
	 *
	 * @param appSecret app secret
	 * @param data      data to sign
	 * @return hex sign
	 */
	public static String hmacSha256Hex(String appSecret, String data) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return toHex(bytes);
		} catch (Exception e) {
			throw new RuntimeException("openapi sign error: " + e.getMessage(), e);
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

	/**
	 * constant-time compare to avoid timing attack
	 *
	 * @param expected expected sign
	 * @param actual   actual sign
	 * @return matched or not
	 */
	public static boolean secureEquals(String expected, String actual) {
		if (expected == null || actual == null) {
			return false;
		}
		byte[] a = expected.getBytes(StandardCharsets.UTF_8);
		byte[] b = actual.getBytes(StandardCharsets.UTF_8);
		if (a.length != b.length) {
			return false;
		}
		int diff = 0;
		for (int i = 0; i < a.length; i++) {
			diff |= a[i] ^ b[i];
		}
		return diff == 0;
	}

}
