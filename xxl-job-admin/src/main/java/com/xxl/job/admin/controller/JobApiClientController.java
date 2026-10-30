package com.xxl.job.admin.controller;

import com.xxl.job.admin.controller.annotation.PermissionLimit;
import com.xxl.job.admin.core.model.XxlJobApiClient;
import com.xxl.job.admin.core.model.XxlJobUser;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.admin.dao.XxlJobApiClientDao;
import com.xxl.job.admin.dao.XxlJobUserDao;
import com.xxl.job.core.biz.model.ReturnT;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放API凭证管理（AppKey/AppSecret）。
 *
 * @author xxl-job-pro
 */
@Controller
@RequestMapping("/apiclient")
public class JobApiClientController {

	@Resource
	private XxlJobApiClientDao xxlJobApiClientDao;
	@Resource
	private XxlJobUserDao xxlJobUserDao;

	@RequestMapping
	@PermissionLimit(adminuser = true)
	public String index() {
		// vue2 页面初始化数据（可绑定账号列表）已迁移至 /apiclient/initData
		return "apiclient/index";
	}

	/**
	 * vue2 页面初始化数据：可绑定的账号列表，替代原 model.addAttribute 服务端渲染。
	 */
	@RequestMapping("/initData")
	@ResponseBody
	@PermissionLimit(adminuser = true)
	public ReturnT<Map<String, Object>> initData() {
		List<Map<String, Object>> userData = new ArrayList<Map<String, Object>>();
		List<XxlJobUser> userList = xxlJobUserDao.pageList(0, 1000, null, -1);
		if (userList != null) {
			for (XxlJobUser user : userList) {
				Map<String, Object> m = new HashMap<String, Object>();
				m.put("username", user.getUsername());
				userData.add(m);
			}
		}
		Map<String, Object> data = new HashMap<String, Object>();
		data.put("userList", userData);
		return new ReturnT<Map<String, Object>>(data);
	}

	@RequestMapping("/pageList")
	@ResponseBody
	@PermissionLimit(adminuser = true)
	public Map<String, Object> pageList(@RequestParam(required = false, defaultValue = "0") int start,
										@RequestParam(required = false, defaultValue = "10") int length,
										String appKey) {
		List<XxlJobApiClient> list = xxlJobApiClientDao.pageList(start, length, appKey);
		int list_count = xxlJobApiClientDao.pageListCount(start, length, appKey);

		// mask secret
		if (list != null && list.size() > 0) {
			for (XxlJobApiClient item : list) {
				item.setAppSecret("********");
			}
		}

		Map<String, Object> maps = new HashMap<String, Object>();
		maps.put("recordsTotal", list_count);
		maps.put("recordsFiltered", list_count);
		maps.put("data", list);
		return maps;
	}

	@RequestMapping("/add")
	@ResponseBody
	@PermissionLimit(adminuser = true)
	public ReturnT<String> add(XxlJobApiClient xxlJobApiClient) {

		// valid appKey
		if (!StringUtils.hasText(xxlJobApiClient.getAppKey())) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, I18nUtil.getString("system_please_input") + I18nUtil.getString("apiclient_app_key"));
		}
		xxlJobApiClient.setAppKey(xxlJobApiClient.getAppKey().trim());
		// valid appSecret
		if (!StringUtils.hasText(xxlJobApiClient.getAppSecret())) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, I18nUtil.getString("system_please_input") + I18nUtil.getString("apiclient_app_secret"));
		}
		xxlJobApiClient.setAppSecret(xxlJobApiClient.getAppSecret().trim());
		// valid bind user
		if (!StringUtils.hasText(xxlJobApiClient.getUsername())) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, I18nUtil.getString("system_please_choose") + I18nUtil.getString("apiclient_bind_user"));
		}

		// check repeat
		if (xxlJobApiClientDao.loadByAppKey(xxlJobApiClient.getAppKey()) != null
				|| isAppKeyExists(xxlJobApiClient.getAppKey())) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, I18nUtil.getString("apiclient_app_key_repeat"));
		}

		Date now = new Date();
		xxlJobApiClient.setAddTime(now);
		xxlJobApiClient.setUpdateTime(now);
		xxlJobApiClientDao.save(xxlJobApiClient);
		return ReturnT.SUCCESS;
	}

	@RequestMapping("/update")
	@ResponseBody
	@PermissionLimit(adminuser = true)
	public ReturnT<String> update(XxlJobApiClient xxlJobApiClient) {

		XxlJobApiClient exist = xxlJobApiClientDao.loadById(xxlJobApiClient.getId());
		if (exist == null) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, I18nUtil.getString("apiclient_not_exist"));
		}
		if (!StringUtils.hasText(xxlJobApiClient.getUsername())) {
			return new ReturnT<String>(ReturnT.FAIL_CODE, I18nUtil.getString("system_please_choose") + I18nUtil.getString("apiclient_bind_user"));
		}

		exist.setAppSecret(StringUtils.hasText(xxlJobApiClient.getAppSecret()) ? xxlJobApiClient.getAppSecret().trim() : exist.getAppSecret());
		exist.setUsername(xxlJobApiClient.getUsername().trim());
		exist.setCallbackUrl(xxlJobApiClient.getCallbackUrl());
		exist.setStatus(xxlJobApiClient.getStatus());
		exist.setRemark(xxlJobApiClient.getRemark());
		exist.setUpdateTime(new Date());

		xxlJobApiClientDao.update(exist);
		return ReturnT.SUCCESS;
	}

	@RequestMapping("/remove")
	@ResponseBody
	@PermissionLimit(adminuser = true)
	public ReturnT<String> remove(int id) {
		xxlJobApiClientDao.delete(id);
		return ReturnT.SUCCESS;
	}

	// appKey 是否已存在（含禁用），loadByAppKey 仅返回启用记录，故这里用分页查询兜底
	private boolean isAppKeyExists(String appKey) {
		List<XxlJobApiClient> list = xxlJobApiClientDao.pageList(0, 1000, appKey);
		if (list != null) {
			for (XxlJobApiClient item : list) {
				if (appKey.equals(item.getAppKey())) {
					return true;
				}
			}
		}
		return false;
	}

}
