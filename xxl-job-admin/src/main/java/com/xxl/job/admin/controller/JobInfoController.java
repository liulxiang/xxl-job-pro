package com.xxl.job.admin.controller;

import com.xxl.job.admin.controller.interceptor.PermissionInterceptor;
import com.xxl.job.admin.core.exception.XxlJobException;
import com.xxl.job.admin.core.model.XxlJobGroup;
import com.xxl.job.admin.core.model.XxlJobInfo;
import com.xxl.job.admin.core.model.XxlJobUser;
import com.xxl.job.admin.core.route.ExecutorRouteStrategyEnum;
import com.xxl.job.admin.core.scheduler.MisfireStrategyEnum;
import com.xxl.job.admin.core.scheduler.ScheduleTypeEnum;
import com.xxl.job.admin.core.thread.JobScheduleHelper;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.dao.XxlJobGroupDao;
import com.xxl.job.admin.service.XxlJobService;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.enums.ExecutorBlockStrategyEnum;
import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * index controller
 * @author xuxueli 2015-12-19 16:13:16
 */
@Controller
@RequestMapping("/jobinfo")
public class JobInfoController {
	private static Logger logger = LoggerFactory.getLogger(JobInfoController.class);

	@Resource
	private XxlJobGroupDao xxlJobGroupDao;
	@Resource
	private XxlJobService xxlJobService;
	
	@RequestMapping
	public String index(HttpServletRequest request, @RequestParam(required = false, defaultValue = "-1") int jobGroup) {

		// 执行器权限校验：vue2 页面初始化数据已迁移至 /jobinfo/initData

		// 执行器列表
		List<XxlJobGroup> jobGroupList_all =  xxlJobGroupDao.findAll();

		// filter group
		List<XxlJobGroup> jobGroupList = PermissionInterceptor.filterJobGroupByRole(request, jobGroupList_all);
		if (jobGroupList==null || jobGroupList.size()==0) {
			throw new XxlJobException(I18nUtil.getString("jobgroup_empty"));
		}

		return "jobinfo/index";
	}

	/**
	 * vue2 页面初始化数据：枚举字典 + 执行器列表 + 登录用户，替代原 model.addAttribute 服务端渲染。
	 */
	@RequestMapping("/initData")
	@ResponseBody
	public ReturnT<Map<String, Object>> initData(HttpServletRequest request, @RequestParam(required = false, defaultValue = "-1") int jobGroup) {

		Map<String, Object> data = new HashMap<String, Object>();

		// 枚举-字典
		List<Map<String, Object>> scheduleTypeList = new ArrayList<Map<String, Object>>();
		for (ScheduleTypeEnum item : ScheduleTypeEnum.values()) {
			scheduleTypeList.add(enumItem(item.name(), item.getTitle()));
		}
		data.put("scheduleTypeList", scheduleTypeList);

		List<Map<String, Object>> misfireStrategyList = new ArrayList<Map<String, Object>>();
		for (MisfireStrategyEnum item : MisfireStrategyEnum.values()) {
			misfireStrategyList.add(enumItem(item.name(), item.getTitle()));
		}
		data.put("misfireStrategyList", misfireStrategyList);

		List<Map<String, Object>> executorRouteStrategyList = new ArrayList<Map<String, Object>>();
		for (ExecutorRouteStrategyEnum item : ExecutorRouteStrategyEnum.values()) {
			executorRouteStrategyList.add(enumItem(item.name(), item.getTitle()));
		}
		data.put("executorRouteStrategyList", executorRouteStrategyList);

		List<Map<String, Object>> executorBlockStrategyList = new ArrayList<Map<String, Object>>();
		for (ExecutorBlockStrategyEnum item : ExecutorBlockStrategyEnum.values()) {
			executorBlockStrategyList.add(enumItem(item.name(), item.getTitle()));
		}
		data.put("executorBlockStrategyList", executorBlockStrategyList);

		List<Map<String, Object>> glueTypeList = new ArrayList<Map<String, Object>>();
		for (GlueTypeEnum item : GlueTypeEnum.values()) {
			glueTypeList.add(enumItem(item.name(), item.getDesc()));
		}
		data.put("glueTypeList", glueTypeList);

		// 执行器列表（按角色过滤）
		List<XxlJobGroup> jobGroupList_all = xxlJobGroupDao.findAll();
		List<XxlJobGroup> jobGroupList = PermissionInterceptor.filterJobGroupByRole(request, jobGroupList_all);
		List<Map<String, Object>> jobGroupData = new ArrayList<Map<String, Object>>();
		if (jobGroupList != null) {
			for (XxlJobGroup group : jobGroupList) {
				Map<String, Object> m = new HashMap<String, Object>();
				m.put("id", group.getId());
				m.put("title", group.getTitle());
				jobGroupData.add(m);
			}
		}
		data.put("jobGroupList", jobGroupData);
		data.put("jobGroup", jobGroup);

		// 登录用户（供前端 GLUE 显隐 / 只读判断）
		XxlJobUser loginUser = PermissionInterceptor.getLoginUser(request);
		Map<String, Object> loginUserMap = new HashMap<String, Object>();
		if (loginUser != null) {
			loginUserMap.put("id", loginUser.getId());
			loginUserMap.put("username", loginUser.getUsername());
			loginUserMap.put("role", loginUser.getRole());
			loginUserMap.put("permission", loginUser.getPermission());
		}
		data.put("loginUser", loginUserMap);

		return new ReturnT<Map<String, Object>>(data);
	}

	private static Map<String, Object> enumItem(String name, String title) {
		Map<String, Object> m = new HashMap<String, Object>();
		m.put("name", name);
		m.put("title", title);
		return m;
	}

	@RequestMapping("/pageList")
	@ResponseBody
	public Map<String, Object> pageList(@RequestParam(required = false, defaultValue = "0") int start,  
			@RequestParam(required = false, defaultValue = "10") int length,
			int jobGroup, int triggerStatus, String jobDesc, String executorHandler, String author) {
		
		return xxlJobService.pageList(start, length, jobGroup, triggerStatus, jobDesc, executorHandler, author);
	}
	
	@RequestMapping("/add")
	@ResponseBody
	public ReturnT<String> add(HttpServletRequest request, XxlJobInfo jobInfo) {
		// valid permission
		PermissionInterceptor.validJobGroupPermission(request, jobInfo.getJobGroup());

		// opt
		XxlJobUser loginUser = PermissionInterceptor.getLoginUser(request);
		return xxlJobService.add(jobInfo, loginUser);
	}
	
	@RequestMapping("/update")
	@ResponseBody
	public ReturnT<String> update(HttpServletRequest request, XxlJobInfo jobInfo) {
		// valid permission
		PermissionInterceptor.validJobGroupPermission(request, jobInfo.getJobGroup());

		// opt
		XxlJobUser loginUser = PermissionInterceptor.getLoginUser(request);
		return xxlJobService.update(jobInfo, loginUser);
	}
	
	@RequestMapping("/remove")
	@ResponseBody
	public ReturnT<String> remove(int id) {
		return xxlJobService.remove(id);
	}
	
	@RequestMapping("/stop")
	@ResponseBody
	public ReturnT<String> pause(int id) {
		return xxlJobService.stop(id);
	}
	
	@RequestMapping("/start")
	@ResponseBody
	public ReturnT<String> start(int id) {
		return xxlJobService.start(id);
	}
	
	@RequestMapping("/trigger")
	@ResponseBody
	public ReturnT<String> triggerJob(HttpServletRequest request, int id, String executorParam, String addressList) {
		// login user
		XxlJobUser loginUser = PermissionInterceptor.getLoginUser(request);
		// trigger
		return xxlJobService.trigger(loginUser, id, executorParam, addressList);
	}

	@RequestMapping("/nextTriggerTime")
	@ResponseBody
	public ReturnT<List<String>> nextTriggerTime(String scheduleType, String scheduleConf) {

		XxlJobInfo paramXxlJobInfo = new XxlJobInfo();
		paramXxlJobInfo.setScheduleType(scheduleType);
		paramXxlJobInfo.setScheduleConf(scheduleConf);

		List<String> result = new ArrayList<>();
		try {
			Date lastTime = new Date();
			for (int i = 0; i < 5; i++) {
				lastTime = JobScheduleHelper.generateNextValidTime(paramXxlJobInfo, lastTime);
				if (lastTime != null) {
					result.add(DateUtil.formatDateTime(lastTime));
				} else {
					break;
				}
			}
		} catch (Exception e) {
			logger.error("nextTriggerTime error. scheduleType = {}, scheduleConf= {}", scheduleType, scheduleConf, e);
			return new ReturnT<List<String>>(ReturnT.FAIL_CODE, (I18nUtil.getString("schedule_type")+I18nUtil.getString("system_unvalid")) + e.getMessage());
		}
		return new ReturnT<List<String>>(result);

	}
	
}
