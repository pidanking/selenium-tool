package com.example.seleniumtool.browser;

import com.example.seleniumtool.config.AutomationProperties;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class WebDriverFactory {

    private final AutomationProperties properties;

    public WebDriverFactory(AutomationProperties properties) {
        this.properties = properties;
    }

    public RemoteWebDriver createChromeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);

        if (properties.getBrowser().isDisableImages()) {
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("profile.managed_default_content_settings.images", 2);
            options.setExperimentalOption("prefs", prefs);
        }

        if (properties.getBrowser().isHeadless()) {
            options.addArguments("--headless=new");
        }
        if (properties.getBrowser().isNoSandbox()) {
            options.addArguments("--no-sandbox");
        }
        if (properties.getBrowser().isDisableDevShmUsage()) {
            options.addArguments("--disable-dev-shm-usage");
        }
        options.addArguments(properties.getBrowser().getArguments());

        String proxy = properties.getBrowser().getProxy();
        if (StringUtils.hasText(proxy)) {
            options.addArguments("--proxy-server=" + proxy.trim());
        }

        String remoteUrl = properties.getBrowser().getRemoteUrl();
        if (!StringUtils.hasText(remoteUrl)) {
            throw new IllegalStateException("必须配置 automation.browser.remote-url（远程 Selenium 地址）");
        }

        log.info("连接远程 Selenium: {}", remoteUrl);
        try {
            RemoteWebDriver driver = new RemoteWebDriver(new URL(remoteUrl.trim()), options);
            driver.manage().timeouts().pageLoadTimeout(properties.getBrowser().getPageLoadTimeout());
            log.info("远程浏览器会话已建立");
            return driver;
        } catch (Exception e) {
            throw new IllegalStateException("连接远程 Selenium 失败: " + remoteUrl, e);
        }
    }
}
