package com.xxl.job.admin.dao;

import com.xxl.job.admin.core.model.XxlJobApiClient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 开放API接入客户端 dao
 *
 * @author xxl-job-pro
 */
@Mapper
public interface XxlJobApiClientDao {

	public List<XxlJobApiClient> pageList(@Param("offset") int offset,
										  @Param("pagesize") int pagesize,
										  @Param("appKey") String appKey);

	public int pageListCount(@Param("offset") int offset,
							 @Param("pagesize") int pagesize,
							 @Param("appKey") String appKey);

	/**
	 * 根据 appKey 加载启用的客户端（鉴权用，仅返回 status=1）
	 *
	 * @param appKey appKey
	 * @return client, null if not exists
	 */
	public XxlJobApiClient loadByAppKey(@Param("appKey") String appKey);

	public XxlJobApiClient loadById(@Param("id") int id);

	public int save(XxlJobApiClient xxlJobApiClient);

	public int update(XxlJobApiClient xxlJobApiClient);

	public int delete(@Param("id") int id);

}
