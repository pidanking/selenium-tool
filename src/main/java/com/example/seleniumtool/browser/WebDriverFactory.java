package com.example.seleniumtool.browser;

import com.example.seleniumtool.config.AutomationProperties;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v147.network.Network;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class WebDriverFactory {

    private final AutomationProperties properties;

    public WebDriverFactory(AutomationProperties properties) {
        this.properties = properties;
        if (!isRemoteMode()) {
            writeSeleniumManagerProxyConfig();
        }
    }

    private boolean isRemoteMode() {
        return StringUtils.hasText(properties.getBrowser().getRemoteUrl());
    }

    private void writeSeleniumManagerProxyConfig() {
        String proxy = resolveProxy();
        if (proxy == null) {
            log.info("未检测到代理配置，Selenium Manager 将直连下载 chromedriver");
            return;
        }
        String content = "proxy = \"" + proxy + "\"" + System.lineSeparator();
        try {
            Path cacheDir = getSeleniumCacheDir();
            Files.createDirectories(cacheDir);
            Path tomlPath = cacheDir.resolve("se-config.toml");
            Files.writeString(tomlPath, content);
            log.info("已写入 Selenium Manager 代理配置: {} -> {}", tomlPath, proxy);
        } catch (IOException e) {
            log.warn("写入 se-config.toml 代理配置失败: {}", e.getMessage());
        }
    }

    private String resolveProxy() {
        String configuredProxy = properties.getBrowser().getProxy();
        if (StringUtils.hasText(configuredProxy)) {
            return configuredProxy.trim();
        }
        return null;
    }

    private Path getSeleniumCacheDir() {
        String userHome = System.getProperty("user.home");
        return Path.of(userHome, ".cache", "selenium");
    }

    /**
     * 创建 WebDriver：远程模式连接 Selenium Grid，本地模式启动 ChromeDriver。
     */
    public RemoteWebDriver createChromeDriver() {
        ChromeOptions options = buildOptions();

        if (isRemoteMode()) {
            return createRemoteDriver(options);
        }
        return createLocalDriver(options);
    }

    private ChromeOptions buildOptions() {
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);

        if (properties.getBrowser().isDisableImages()) {
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("profile.managed_default_content_settings.images", 2);
            options.setExperimentalOption("prefs", prefs);
            log.info("已通过 Chrome prefs 禁止图片加载");
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
        if (!isRemoteMode() && StringUtils.hasText(properties.getBrowser().getBinaryPath())) {
            options.setBinary(properties.getBrowser().getBinaryPath());
        }
        options.addArguments(properties.getBrowser().getArguments());
        Duration pageLoadTimeout = properties.getBrowser().getPageLoadTimeout();
        options.setPageLoadTimeout(pageLoadTimeout);

        String proxy = resolveProxy();
        if (proxy != null) {
            options.addArguments("--proxy-server=" + proxy);
            log.info("已为 Chrome 浏览器配置代理: {}", proxy);
        }

        return options;
    }

    /**
     * 连接远程 Selenium Grid（Docker Compose 部署模式）。
     */
    private RemoteWebDriver createRemoteDriver(ChromeOptions options) {
        String remoteUrl = properties.getBrowser().getRemoteUrl().trim();
        log.info("连接远程 Selenium Grid: {}", remoteUrl);
        try {
            RemoteWebDriver driver = new RemoteWebDriver(new URL(remoteUrl), options);
            log.info("远程浏览器会话已建立");
            applyResourceBlocking(driver);
            return driver;
        } catch (Exception e) {
            throw new IllegalStateException("连接远程 Selenium 失败: " + remoteUrl, e);
        }
    }

    /**
     * 本地模式启动 ChromeDriver。
     */
    private ChromeDriver createLocalDriver(ChromeOptions options) {
        ChromeDriver driver;
        if (StringUtils.hasText(properties.getBrowser().getDriverPath())) {
            File driverFile = new File(properties.getBrowser().getDriverPath());
            if (!driverFile.exists()) {
                throw new IllegalStateException(
                    "配置的 chromedriver 路径不存在: " + driverFile.getAbsolutePath());
            }
            ChromeDriverService service = new ChromeDriverService.Builder()
                .usingDriverExecutable(driverFile)
                .build();
            driver = new ChromeDriver(service, options);
        } else {
            log.warn("未配置 driver-path，将使用 Selenium Manager 自动下载 ChromeDriver");
            driver = new ChromeDriver(options);
        }
        applyResourceBlocking(driver);
        return driver;
    }

    private void applyResourceBlocking(RemoteWebDriver driver) {
        List<String> suffixes = properties.getBrowser().getBlockResourceSuffixes();
        if (suffixes != null && !suffixes.isEmpty() && driver instanceof ChromeDriver cd) {
            enableResourceBlocking(cd, suffixes);
        }
    }

    private void enableResourceBlocking(ChromeDriver driver, List<String> suffixes) {
        try {
            List<String> patterns = suffixes.stream()
                    .flatMap(ext -> Stream.of("*." + ext, "*." + ext + "?*"))
                    .toList();
            DevTools devTools = driver.getDevTools();
            devTools.createSessionIfThereIsNotOne();
            devTools.send(Network.enable(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));
            devTools.send(Network.setBlockedURLs(Optional.empty(), Optional.of(patterns)));
            log.info("已通过 CDP 阻止以下后缀资源加载: {}", suffixes);
        } catch (Exception e) {
            log.warn("CDP 资源阻止配置失败: {}", e.getMessage());
        }
    }
}
