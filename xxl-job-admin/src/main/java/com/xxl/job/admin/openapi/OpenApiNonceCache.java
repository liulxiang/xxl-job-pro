package com.xxl.job.admin.openapi;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 防重放 nonce 缓存。
 *
 * <p>记录已使用过的 nonce 及其过期时间，窗口内的重复 nonce 会被拒绝。
 * 采用本地内存实现，适用于调度中心单机或小规模集群；集群环境下若需严格防重放，
 * 可替换为 Redis 等共享存储。</p>
 *
 * @author xxl-job-pro
 */
@Component
public class OpenApiNonceCache {

	/**
	 * nonce -> expireAt(ms)
	 */
	private final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>();

	/**
	 * put nonce if absent and not expired.
	 *
	 * @param nonce       nonce
	 * @param ttlMillis   ttl
	 * @return true if the nonce is fresh (accepted), false if replayed (rejected)
	 */
	public boolean putIfAbsent(String nonce, long ttlMillis) {
		long now = System.currentTimeMillis();
		expire(now);

		Long existing = cache.putIfAbsent(nonce, now + ttlMillis);
		if (existing == null) {
			return true;    // fresh
		}
		if (existing < now) {
			// previous entry already expired, refresh it
			cache.put(nonce, now + ttlMillis);
			return true;
		}
		return false;       // replay
	}

	private void expire(long now) {
		Iterator<Map.Entry<String, Long>> it = cache.entrySet().iterator();
		while (it.hasNext()) {
			if (it.next().getValue() < now) {
				it.remove();
			}
		}
	}

}
