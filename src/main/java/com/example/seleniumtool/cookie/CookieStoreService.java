package com.example.seleniumtool.cookie;

import com.example.seleniumtool.config.AutomationProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 基于 JSON 文件的 Cookie 存储服务。
 * 支持按 target 分组管理手动配置的 Cookie。
 */
@Service
public class CookieStoreService {

    private static final Logger log = LoggerFactory.getLogger(CookieStoreService.class);
    private static final String DEFAULT_STORE_FILE = "cookie-store.json";

    private final ObjectMapper objectMapper;
    private final AutomationProperties properties;
    private final Map<String, List<CookieEntry>> store = new ConcurrentHashMap<>();
    private final Path storeFile;

    public CookieStoreService(ObjectMapper objectMapper, AutomationProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.storeFile = resolveStoreFile();
        load();
    }

    private Path resolveStoreFile() {
        Path path = Paths.get(DEFAULT_STORE_FILE);
        return path.isAbsolute() ? path : path.toAbsolutePath().normalize();
    }

    /** 获取所有 Cookie（按 target 分组） */
    public Map<String, List<CookieEntry>> getAll() {
        return Map.copyOf(store);
    }

    /** 获取指定 target 的 Cookie 列表 */
    public List<CookieEntry> getByTarget(String targetName) {
        return new ArrayList<>(store.getOrDefault(targetName, List.of()));
    }

    /** 获取所有 Cookie 的扁平列表 */
    public List<CookieEntry> listAll() {
        return store.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /** 添加 Cookie */
    public CookieEntry add(CookieEntry entry) {
        if (entry.getId() == null || entry.getId().isBlank()) {
            entry.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        store.computeIfAbsent(entry.getTargetName(), k -> new ArrayList<>()).add(entry);
        save();
        log.info("已添加 Cookie [{}] {} -> {}", entry.getId(), entry.getName(), entry.getTargetName());
        return entry;
    }

    /** 更新 Cookie */
    public Optional<CookieEntry> update(String id, CookieEntry updated) {
        for (var entry : store.entrySet()) {
            List<CookieEntry> list = entry.getValue();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId().equals(id)) {
                    updated.setId(id);
                    list.set(i, updated);
                    save();
                    log.info("已更新 Cookie [{}]", id);
                    return Optional.of(updated);
                }
            }
        }
        return Optional.empty();
    }

    /** 删除 Cookie */
    public boolean delete(String id) {
        for (var entry : store.entrySet()) {
            boolean removed = entry.getValue().removeIf(c -> c.getId().equals(id));
            if (removed) {
                if (entry.getValue().isEmpty()) {
                    store.remove(entry.getKey());
                }
                save();
                log.info("已删除 Cookie [{}]", id);
                return true;
            }
        }
        return false;
    }

    /** 按 target 删除所有 Cookie */
    public int deleteByTarget(String targetName) {
        List<CookieEntry> removed = store.remove(targetName);
        if (removed != null && !removed.isEmpty()) {
            save();
            log.info("已删除 target [{}] 下的 {} 个 Cookie", targetName, removed.size());
            return removed.size();
        }
        return 0;
    }

    /** 将手动配置的 Cookie 转换为 CookieCloudCookie 格式，供浏览器注入使用 */
    public List<CookieCloudCookie> toCookieCloudCookies(String targetName) {
        return getByTarget(targetName).stream()
                .map(this::toCookieCloudCookie)
                .collect(Collectors.toList());
    }

    private CookieCloudCookie toCookieCloudCookie(CookieEntry entry) {
        return new CookieCloudCookie(
                entry.getDomain(),
                entry.getName(),
                entry.getValue(),
                entry.getPath() != null ? entry.getPath() : "/",
                entry.isSecure(),
                entry.isHttpOnly()
        );
    }

    private void load() {
        try {
            if (!Files.exists(storeFile)) {
                log.info("Cookie 存储文件不存在，初始化空存储: {}", storeFile);
                return;
            }
            String json = Files.readString(storeFile);
            if (json.isBlank()) {
                return;
            }
            var tree = objectMapper.readTree(json);
            if (tree.isArray()) {
                for (var node : tree) {
                    CookieEntry entry = objectMapper.treeToValue(node, CookieEntry.class);
                    store.computeIfAbsent(entry.getTargetName(), k -> new ArrayList<>()).add(entry);
                }
                log.info("已加载 {} 个手动配置的 Cookie", listAll().size());
            }
        } catch (Exception ex) {
            log.warn("加载 Cookie 存储文件失败: {}", storeFile, ex);
        }
    }

    private synchronized void save() {
        try {
            Path parent = storeFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<CookieEntry> all = listAll();
            Files.writeString(storeFile, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(all));
        } catch (Exception ex) {
            log.warn("保存 Cookie 存储文件失败: {}", storeFile, ex);
        }
    }
}
