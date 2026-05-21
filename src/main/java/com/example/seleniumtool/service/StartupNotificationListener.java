package com.example.seleniumtool.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

public class StartupNotificationListener implements ApplicationListener<ApplicationEvent> {

    private static final String WEBHOOK_URL_KEY = "automation.startup-notification.webhook-url";
    private static final String DEFAULT_WEBHOOK_PLACEHOLDER = "webhook地址";
    private static final String DEFAULT_FAILURE_MESSAGE = "selenium-tool启动失败";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ApplicationFailedEvent failedEvent) {
            sendFailure(failedEvent);
        }
    }

    private void sendFailure(ApplicationFailedEvent event) {
        Environment environment = event.getApplicationContext() != null ? event.getApplicationContext().getEnvironment() : null;

        String webhookUrl = environment != null ? environment.getProperty(WEBHOOK_URL_KEY, "") : "";
        if (!StringUtils.hasText(webhookUrl) || DEFAULT_WEBHOOK_PLACEHOLDER.equals(webhookUrl)) {
            return;
        }

        String message = "【selenium-tool】启动失败\n内容: "
            + DEFAULT_FAILURE_MESSAGE
            + "\n异常: "
            + extractExceptionMessage(event.getException());
        String payload = "{\"msgtype\":\"text\",\"text\":{\"content\":\"" + escapeJson(message) + "\"}}";
        try {
            httpClient.send(
                HttpRequest.newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
        } catch (Exception ignored) {
        }
    }

    private String extractExceptionMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                return current.getMessage().replace('\r', ' ').replace('\n', ' ');
            }
            current = current.getCause();
        }
        return throwable == null ? "unknown error" : throwable.getClass().getSimpleName();
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }
}
