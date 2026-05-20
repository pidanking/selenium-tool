package com.example.seleniumtool.service;

import com.example.seleniumtool.config.AutomationProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WebhookNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificationService.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AutomationProperties properties;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public WebhookNotificationService(AutomationProperties properties) {
        this.properties = properties;
    }

    public void send(String status, String content) {
        try {
            sendInternal(status, content);
        } catch (Exception ex) {
            log.warn("Webhook 消息发送失败", ex);
        }
    }

    public void sendOrThrow(String status, String content) {
        try {
            sendInternal(status, content);
        } catch (Exception ex) {
            log.error("Webhook 消息发送失败，状态 {}", status, ex);
            throw new IllegalStateException("Webhook 消息发送失败: " + status, ex);
        }
    }

    private void sendInternal(String status, String content) throws Exception {
        if (!properties.getStartupNotification().isEnabled()) {
            return;
        }

        String webhookUrl = properties.getStartupNotification().getWebhookUrl();

        String message = "【selenium-tool】" + status
            + "\n时间: " + LocalDateTime.now().format(TIME_FORMATTER)
            + "\n内容: " + content;

        String payload = "{\"msgtype\":\"text\",\"text\":{\"content\":\"" + escapeJson(message) + "\"}}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
            .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        log.info("Webhook 消息发送成功");
    }

    private String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }
}
