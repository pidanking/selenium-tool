package com.example.seleniumtool.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutomationScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutomationScheduler.class);

    private final BrowserAutomationService browserAutomationService;
    private final AutomationAlertState automationAlertState;
    private final WebhookNotificationService webhookNotificationService;

    public AutomationScheduler(
        BrowserAutomationService browserAutomationService,
        AutomationAlertState automationAlertState,
        WebhookNotificationService webhookNotificationService
    ) {
        this.browserAutomationService = browserAutomationService;
        this.automationAlertState = automationAlertState;
        this.webhookNotificationService = webhookNotificationService;
    }

    /**
     * 按 cron 定时触发自动化任务。
     */
    @Scheduled(cron = "${automation.schedule.cron}", zone = "${automation.schedule.zone}")
    public void runDailyJob() {
        log.info("Triggered scheduled automation task");
        String status = "定时任务定时执行成功";
        String content = "selenium-tool定时任务定时执行成功";
        try {
            browserAutomationService.executeOnce();
        } catch (Exception ex) {
            status = "定时任务定时执行失败";
            content = "selenium-tool定时任务定时执行失败\n异常: " + ex.getMessage();
            log.error("Scheduled automation task failed", ex);
        } finally {
            webhookNotificationService.send(status, buildScheduledSummary(content));
        }
    }

    private String buildScheduledSummary(String content) {
        StringBuilder summary = new StringBuilder(content);
        List<String> failures = automationAlertState.drainTargetFailures();
        if(failures.isEmpty()){
            summary.append("\n\n全部打开成功");
        }else{
            summary.append("\n\n打开失败:\n");
            summary.append(String.join("\n\n", failures));
        }
        return summary.toString();
    }
}
