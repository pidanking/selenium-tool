package com.example.seleniumtool.service;

import com.example.seleniumtool.config.AutomationProperties;
import com.example.seleniumtool.cookie.CookieCloudClient;
import com.example.seleniumtool.cookie.CookieCloudCookie;
import com.example.seleniumtool.cookie.CookieEntry;
import com.example.seleniumtool.cookie.CookieStoreService;
import tools.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 配置校验服务：检查 targets、Cookie、CookieCloud 连通性等。
 */
@Service
public class ConfigValidationService {

    private static final Logger log = LoggerFactory.getLogger(ConfigValidationService.class);

    private final AutomationProperties properties;
    private final CookieStoreService cookieStoreService;
    private final CookieCloudClient cookieCloudClient;

    public ConfigValidationService(
            AutomationProperties properties,
            CookieStoreService cookieStoreService,
            CookieCloudClient cookieCloudClient
    ) {
        this.properties = properties;
        this.cookieStoreService = cookieStoreService;
        this.cookieCloudClient = cookieCloudClient;
    }

    /**
     * 执行完整校验，返回结构化结果。
     */
    public Map<String, Object> validate() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> checks = new ArrayList<>();
        boolean allPassed = true;

        // 1. 检查 targets 配置
        Map<String, Object> targetCheck = checkTargets();
        checks.add(targetCheck);
        if (!Boolean.TRUE.equals(targetCheck.get("passed"))) allPassed = false;

        // 2. 检查每个 target 的 Cookie
        for (AutomationProperties.Target target : properties.getTargets()) {
            Map<String, Object> cookieCheck = checkTargetCookies(target);
            checks.add(cookieCheck);
            if (!Boolean.TRUE.equals(cookieCheck.get("passed"))) allPassed = false;
        }

        // 3. 检查 CookieCloud 连通性
        Map<String, Object> ccCheck = checkCookieCloud();
        checks.add(ccCheck);
        if (!Boolean.TRUE.equals(ccCheck.get("passed"))) allPassed = false;

        // 4. 检查定时任务配置
        Map<String, Object> scheduleCheck = checkSchedule();
        checks.add(scheduleCheck);
        if (!Boolean.TRUE.equals(scheduleCheck.get("passed"))) allPassed = false;

        result.put("passed", allPassed);
        result.put("checks", checks);
        result.put("summary", allPassed ? "所有检查通过 ✅" : "部分检查未通过 ⚠️");
        return result;
    }

    private Map<String, Object> checkTargets() {
        Map<String, Object> check = new HashMap<>();
        check.put("name", "目标站点配置");
        List<AutomationProperties.Target> targets = properties.getTargets();

        if (targets == null || targets.isEmpty()) {
            check.put("passed", false);
            check.put("message", "未配置任何目标站点（targets 为空）");
            return check;
        }

        List<String> issues = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            AutomationProperties.Target t = targets.get(i);
            if (!StringUtils.hasText(t.getUrl())) {
                issues.add("targets[" + i + "]: url 为空");
            } else {
                try {
                    java.net.URI.create(t.getUrl());
                } catch (Exception e) {
                    issues.add("targets[" + i + "]: url 格式无效 - " + t.getUrl());
                }
            }
        }

        if (issues.isEmpty()) {
            check.put("passed", true);
            check.put("message", "共配置 " + targets.size() + " 个目标站点");
        } else {
            check.put("passed", false);
            check.put("message", String.join("; ", issues));
        }
        return check;
    }

    private Map<String, Object> checkTargetCookies(AutomationProperties.Target target) {
        Map<String, Object> check = new HashMap<>();
        check.put("name", "Cookie 检查 - " + target.getName());

        // 检查手动配置的 Cookie
        List<CookieEntry> manualCookies = cookieStoreService.getByTarget(target.getName());
        int manualCount = manualCookies.size();

        // 检查手动 Cookie 格式
        List<String> formatIssues = new ArrayList<>();
        for (CookieEntry c : manualCookies) {
            List<String> fieldIssues = new ArrayList<>();
            if (!StringUtils.hasText(c.getDomain())) fieldIssues.add("缺少 domain");
            if (!StringUtils.hasText(c.getName())) fieldIssues.add("缺少 name");
            if (c.getValue() == null) fieldIssues.add("缺少 value");
            if (!fieldIssues.isEmpty()) {
                formatIssues.add("Cookie[" + c.getId() + "]: " + String.join(", ", fieldIssues));
            }
        }

        boolean hasCookies = manualCount > 0;
        boolean hasCloud = properties.getCookieCloud() != null && properties.getCookieCloud().isEnabled();

        StringBuilder msg = new StringBuilder();
        msg.append("手动 Cookie: ").append(manualCount).append(" 个");
        if (hasCloud) {
            msg.append("；CookieCloud: 已配置");
        } else {
            msg.append("；CookieCloud: 未配置");
        }

        if (!hasCookies && !hasCloud) {
            check.put("passed", false);
            check.put("message", msg + " ⚠️ 无任何 Cookie 来源，任务可能无法正常登录");
        } else if (!formatIssues.isEmpty()) {
            check.put("passed", false);
            check.put("message", msg + " ⚠️ 格式问题: " + String.join("; ", formatIssues));
        } else {
            check.put("passed", true);
            check.put("message", msg.toString());
        }
        return check;
    }

    private Map<String, Object> checkCookieCloud() {
        Map<String, Object> check = new HashMap<>();
        check.put("name", "CookieCloud 连通性");

        if (properties.getCookieCloud() == null || !properties.getCookieCloud().isEnabled()) {
            check.put("passed", true);
            check.put("message", "CookieCloud 未配置（跳过）");
            return check;
        }

        try {
            JsonNode cookieData = cookieCloudClient.fetchAllCookies();
            if (cookieData != null && cookieData.isObject()) {
                int domainCount = (int) cookieData.properties().stream().count();
                check.put("passed", true);
                check.put("message", "CookieCloud 连接成功，共 " + domainCount + " 个域名");
            } else {
                check.put("passed", false);
                check.put("message", "CookieCloud 返回数据为空");
            }
        } catch (Exception e) {
            check.put("passed", false);
            check.put("message", "CookieCloud 连接失败: " + e.getMessage());
        }
        return check;
    }

    private Map<String, Object> checkSchedule() {
        Map<String, Object> check = new HashMap<>();
        check.put("name", "定时任务配置");

        AutomationProperties.Schedule schedule = properties.getSchedule();
        if (schedule == null || !StringUtils.hasText(schedule.getCron())) {
            check.put("passed", false);
            check.put("message", "未配置 cron 表达式");
            return check;
        }

        String cron = schedule.getCron();
        // 简单校验 cron 格式（六段式）
        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 5 || parts.length > 6) {
            check.put("passed", false);
            check.put("message", "cron 格式异常（需要 5-6 段）: " + cron);
        } else {
            check.put("passed", true);
            check.put("message", "cron: " + cron + " | 时区: " + schedule.getZone());
        }
        return check;
    }
}
