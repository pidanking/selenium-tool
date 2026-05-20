package com.example.seleniumtool.browser;

import com.example.seleniumtool.config.AutomationProperties;
import java.io.File;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
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

        if (properties.getBrowser().isBlockGif()) {
            enableGifBlocking(driver);
        }

        return driver;
    }

    /**
     * 通过 Chrome DevTools Protocol (CDP) 拦截并阻止 GIF 资源加载。
     * <p>
     * 使用 Network.setBlockedURLs 配置 URL 匹配模式，
     * 浏览器会在网络层直接丢弃匹配的请求，不产生任何流量。
     * <ul>
     *   <li>"*.gif" — 匹配以 .gif 结尾的 URL（无查询参数）</li>
     *   <li>"*.gif?*" — 匹配 .gif 后带查询参数的 URL</li>
     * </ul>
     */
    private void enableGifBlocking(ChromeDriver driver) {
        try {
            DevTools devTools = driver.getDevTools();
            devTools.createSession();
            devTools.send(Network.enable(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty()));
            devTools.send(Network.setBlockedURLs(
                    Optional.empty(),
                    Optional.of(List.of("*.gif", "*.gif?*"))));
            log.info("已通过 CDP Network.setBlockedURLs 配置阻止 GIF 资源加载");
        } catch (Exception e) {
            log.warn("CDP 阻止 GIF 资源配置失败，GIF 将正常加载: {}", e.getMessage());
        }
    }
}
