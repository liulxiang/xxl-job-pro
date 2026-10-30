package com.xxl.job.admin.dao;

import com.xxl.job.admin.core.model.XxlJobOpenapiCallback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 开放API Webhook 回调登记 dao
 *
 * @author xxl-job-pro
 */
@Mapper
public interface XxlJobOpenapiCallbackDao {

	/**
	 * 登记一条待回调（按 logId 唯一，重复登记覆盖）
	 */
	public int save(XxlJobOpenapiCallback callback);

	/**
	 * 按 logId 加载待回调记录
	 *
	 * @param logId logId
	 * @return callback or null
	 */
	public XxlJobOpenapiCallback loadByLogId(@Param("logId") long logId);

	/**
	 * 标记已回调
	 *
	 * @param logId     logId
	 * @param oldStatus 期望的旧状态
	 * @param newStatus 新状态
	 * @return affected rows
	 */
	public int updateStatus(@Param("logId") long logId,
							@Param("oldStatus") int oldStatus,
							@Param("newStatus") int newStatus);

	/**
	 * 捞取待补偿回调：status=0 且 next_retry_time 到期 且 未超重试上限
	 *
	 * @param now      当前时间
	 * @param maxRetry 最大重试次数
	 * @param pagesize 批量上限
	 * @return pending callbacks
	 */
	public List<XxlJobOpenapiCallback> findPending(@Param("now") Date now,
													@Param("maxRetry") int maxRetry,
													@Param("pagesize") int pagesize);

	/**
	 * 乐观认领一次重试（基于 next_retry_time 作版本token），并将 retry_count+1、推后 next_retry_time。
	 * 集群下仅一个节点返回受影响行数=1。
	 *
	 * @return affected rows, 1 = claimed by current node
	 */
	public int claim(@Param("logId") long logId,
					 @Param("oldNextRetryTime") Date oldNextRetryTime,
					 @Param("newNextRetryTime") Date newNextRetryTime);

	/**
	 * 仅推后下次重试时间（任务未完成时 defer，不增加重试次数）
	 */
	public int deferNext(@Param("logId") long logId,
						@Param("nextRetryTime") Date nextRetryTime);

}
