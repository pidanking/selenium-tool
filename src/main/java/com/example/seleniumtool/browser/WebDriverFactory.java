package com.example.seleniumtool.browser;

import com.example.seleniumtool.config.AutomationProperties;
import java.io.File;
import java.io.IOException;
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
        writeSeleniumManagerProxyConfig();
    }

    /**
     * 在 Selenium Manager 缓存目录下写入 se-config.toml 代理配置，
     * 使其能通过代理下载 chromedriver。
     * Rust HTTP 客户端不遵循系统代理，需要显式配置。
     */
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

    /**
     * 解析代理地址，优先级：显式配置 > 系统代理自动检测 > null。
     */
    private String resolveProxy() {
        String configuredProxy = properties.getBrowser().getProxy();
        if (StringUtils.hasText(configuredProxy)) {
            return configuredProxy.trim();
        }
        return null;
    }

    /**
     * 获取 Selenium Manager 缓存目录路径（跨平台）。
     * Selenium Manager 在所有平台上默认使用 ~/.cache/selenium。
     */
    private Path getSeleniumCacheDir() {
        String userHome = System.getProperty("user.home");
        return Path.of(userHome, ".cache", "selenium");
    }

    /**
     * 按配置创建 ChromeDriver，兼容本地调试和 Linux 容器运行。
     */
    public RemoteWebDriver createChromeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);

        if (properties.getBrowser().isDisableImages()) {
            Map<String, Object> prefs = new HashMap<>();
            prefs.put("profile.managed_default_content_settings.images", 2);
            options.setExperimentalOption("prefs", prefs);
            log.info("已通过 Chrome prefs 禁止图片加载 (managed_default_content_settings.images=2)");
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
        if (StringUtils.hasText(properties.getBrowser().getBinaryPath())) {
            options.setBinary(properties.getBrowser().getBinaryPath());
        }
        options.addArguments(properties.getBrowser().getArguments());
        Duration pageLoadTimeout = properties.getBrowser().getPageLoadTimeout();
        options.setPageLoadTimeout(pageLoadTimeout);
        // 为 Chrome 浏览器本身配置代理，使 navigate().to() 等网络请求走代理
        String proxy = resolveProxy();
        if (proxy != null) {
            options.addArguments("--proxy-server=" + proxy);
            log.info("已为 Chrome 浏览器配置代理: {}", proxy);
        }

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
            log.warn("未配置 automation.browser.driver-path，将使用 Selenium Manager 自动下载 ChromeDriver。"
                + "在国内网络环境下可能极慢或失败，建议手动下载 chromedriver 并配置 driver-path。");
            driver = new ChromeDriver(options);
        }

        List<String> suffixes = properties.getBrowser().getBlockResourceSuffixes();
        if (suffixes != null && !suffixes.isEmpty()) {
            enableResourceBlocking(driver, suffixes);
        }

        return driver;
    }

    /**
     * 通过 Chrome DevTools Protocol (CDP) 按后缀阻止指定资源加载。
     * <p>
     * 根据配置的后缀列表动态构建 URL 匹配模式（含和不含查询参数两种形式），
     * 通过 Network.setBlockedURLs 在网络层直接丢弃匹配请求，不产生任何流量。
     * <p>
     * 示例后缀：gif、png、jpg、jpeg、webp、svg、bmp、ico
     *
     * @param suffixes 需要阻止的资源文件后缀列表（不含点号）
     */
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
            log.info("已通过 CDP Network.setBlockedURLs 阻止以下后缀资源加载: {}", suffixes);
        } catch (Exception e) {
            log.warn("CDP 资源阻止配置失败，相关资源将正常加载: {}", e.getMessage());
        }
    }
}
