package com.xxl.job.admin.controller;

import com.xxl.job.admin.controller.interceptor.PermissionInterceptor;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobLogGlue;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.dao.XxlJobInfoDao;
import com.xxl.job.admin.dao.XxlJobLogGlueDao;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.glue.GlueTypeEnum;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * job code controller
 * @author xuxueli 2015-12-19 16:13:16
 */
@Controller
@RequestMapping("/jobcode")
public class JobCodeController {
	
	@Resource
	private XxlJobInfoDao xxlJobInfoDao;
	@Resource
	private XxlJobLogGlueDao xxlJobLogGlueDao;

	@RequestMapping
	public String index(HttpServletRequest request, @RequestParam int jobId) {
		XxlJobInfo jobInfo = xxlJobInfoDao.loadById(jobId);

		if (jobInfo == null) {
			throw new RuntimeException(I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
		}
		if (GlueTypeEnum.BEAN == GlueTypeEnum.match(jobInfo.getGlueType())) {
			throw new RuntimeException(I18nUtil.getString("jobinfo_glue_gluetype_unvalid"));
		}

		// valid permission
		PermissionInterceptor.validJobGroupPermission(request, jobInfo.getJobGroup());

		// vue2 页面初始化数据（任务信息 + 历史版本）已迁移至 /jobcode/initData
		return "jobcode/index";
	}

	/**
	 * vue2 页面初始化数据：任务信息 + GLUE 历史版本，替代原 model.addAttribute 服务端渲染。
	 */
	@RequestMapping("/initData")
	@ResponseBody
	public ReturnT<Map<String, Object>> initData(HttpServletRequest request, @RequestParam int jobId) {
		XxlJobInfo jobInfo = xxlJobInfoDao.loadById(jobId);
		if (jobInfo == null) {
			return new ReturnT<Map<String, Object>>(ReturnT.FAIL_CODE, I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
		}
		if (GlueTypeEnum.BEAN == GlueTypeEnum.match(jobInfo.getGlueType())) {
			return new ReturnT<Map<String, Object>>(ReturnT.FAIL_CODE, I18nUtil.getString("jobinfo_glue_gluetype_unvalid"));
		}

		// valid permission
		PermissionInterceptor.validJobGroupPermission(request, jobInfo.getJobGroup());

		List<XxlJobLogGlue> jobLogGlues = xxlJobLogGlueDao.findByJobId(jobId);

		// 当前版本项（置顶）
		List<Map<String, Object>> versionList = new ArrayList<Map<String, Object>>();
		Map<String, Object> now = new HashMap<String, Object>();
		now.put("version", "version_now");
		now.put("glueType", jobInfo.getGlueType());
		now.put("glueRemark", jobInfo.getGlueRemark());
		now.put("glueSource", jobInfo.getGlueSource());
		versionList.add(now);
		if (jobLogGlues != null) {
			for (XxlJobLogGlue glue : jobLogGlues) {
				Map<String, Object> v = new HashMap<String, Object>();
				v.put("version", "version_" + glue.getId());
				v.put("glueType", glue.getGlueType());
				v.put("glueRemark", glue.getGlueRemark());
				v.put("glueSource", glue.getGlueSource());
				versionList.add(v);
			}
		}

		// GLUE 类型字典（name -> desc），供前端展示类型名称
		List<Map<String, Object>> glueTypeList = new ArrayList<Map<String, Object>>();
		for (GlueTypeEnum item : GlueTypeEnum.values()) {
			Map<String, Object> m = new HashMap<String, Object>();
			m.put("name", item.name());
			m.put("title", item.getDesc());
			glueTypeList.add(m);
		}

		Map<String, Object> infoMap = new HashMap<String, Object>();
		infoMap.put("id", jobInfo.getId());
		infoMap.put("jobDesc", jobInfo.getJobDesc());
		infoMap.put("glueType", jobInfo.getGlueType());

		Map<String, Object> data = new HashMap<String, Object>();
		data.put("jobInfo", infoMap);
		data.put("versionList", versionList);
		data.put("glueTypeList", glueTypeList);
		return new ReturnT<Map<String, Object>>(data);
	}
	
	@RequestMapping("/save")
	@ResponseBody
	public ReturnT<String> save(HttpServletRequest request, int id, String glueSource, String glueRemark) {
		// valid
		if (glueRemark==null) {
			return new ReturnT<String>(500, (I18nUtil.getString("system_please_input") + I18nUtil.getString("jobinfo_glue_remark")) );
		}
		if (glueRemark.length()<4 || glueRemark.length()>100) {
			return new ReturnT<String>(500, I18nUtil.getString("jobinfo_glue_remark_limit"));
		}
		XxlJobInfo existsJobInfo = xxlJobInfoDao.loadById(id);
		if (existsJobInfo == null) {
			return new ReturnT<String>(500, I18nUtil.getString("jobinfo_glue_jobid_unvalid"));
		}

		// valid permission
		PermissionInterceptor.validJobGroupPermission(request, existsJobInfo.getJobGroup());
		
		// update new code
		existsJobInfo.setGlueSource(glueSource);
		existsJobInfo.setGlueRemark(glueRemark);
		existsJobInfo.setGlueUpdatetime(new Date());

		existsJobInfo.setUpdateTime(new Date());
		xxlJobInfoDao.update(existsJobInfo);

		// log old code
		XxlJobLogGlue xxlJobLogGlue = new XxlJobLogGlue();
		xxlJobLogGlue.setJobId(existsJobInfo.getId());
		xxlJobLogGlue.setGlueType(existsJobInfo.getGlueType());
		xxlJobLogGlue.setGlueSource(glueSource);
		xxlJobLogGlue.setGlueRemark(glueRemark);

		xxlJobLogGlue.setAddTime(new Date());
		xxlJobLogGlue.setUpdateTime(new Date());
		xxlJobLogGlueDao.save(xxlJobLogGlue);

		// remove code backup more than 30
		xxlJobLogGlueDao.removeOld(existsJobInfo.getId(), 30);

		return ReturnT.SUCCESS;
	}
	
}
