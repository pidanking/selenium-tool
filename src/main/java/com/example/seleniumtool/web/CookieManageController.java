package com.example.seleniumtool.web;

import com.example.seleniumtool.config.AutomationProperties;
import com.example.seleniumtool.cookie.CookieCloudCookie;
import com.example.seleniumtool.cookie.CookieEntry;
import com.example.seleniumtool.cookie.CookieStoreService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cookies")
public class CookieManageController {

    private final CookieStoreService cookieStoreService;
    private final AutomationProperties properties;

    public CookieManageController(CookieStoreService cookieStoreService, AutomationProperties properties) {
        this.cookieStoreService = cookieStoreService;
        this.properties = properties;
    }

    /** 获取所有 target 列表及其 Cookie */
    @GetMapping("/targets")
    public ResponseEntity<Map<String, Object>> getTargets() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> targets = properties.getTargets().stream().map(t -> {
            Map<String, Object> m = new HashMap<>();
            m.put("name", t.getName());
            m.put("url", t.getUrl());
            m.put("cookieDomain", t.getCookieDomain());
            m.put("cookies", cookieStoreService.getByTarget(t.getName()));
            return m;
        }).collect(Collectors.toList());
        result.put("targets", targets);
        result.put("totalCookies", cookieStoreService.listAll().size());
        return ResponseEntity.ok(result);
    }

    /** 获取指定 target 的 Cookie 列表 */
    @GetMapping("/target/{targetName}")
    public ResponseEntity<List<CookieEntry>> getByTarget(@PathVariable String targetName) {
        return ResponseEntity.ok(cookieStoreService.getByTarget(targetName));
    }

    /** 获取所有 Cookie 扁平列表 */
    @GetMapping
    public ResponseEntity<List<CookieEntry>> listAll() {
        return ResponseEntity.ok(cookieStoreService.listAll());
    }

    /** 添加 Cookie */
    @PostMapping
    public ResponseEntity<CookieEntry> add(@RequestBody CookieEntry entry) {
        return ResponseEntity.ok(cookieStoreService.add(entry));
    }

    /** 更新 Cookie */
    @PutMapping("/{id}")
    public ResponseEntity<CookieEntry> update(@PathVariable String id, @RequestBody CookieEntry entry) {
        return cookieStoreService.update(id, entry)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 删除 Cookie */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        boolean removed = cookieStoreService.delete(id);
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", removed);
        return removed ? ResponseEntity.ok(resp) : ResponseEntity.notFound().build();
    }

    /** 批量删除指定 target 下的 Cookie */
    @DeleteMapping("/target/{targetName}")
    public ResponseEntity<Map<String, Object>> deleteByTarget(@PathVariable String targetName) {
        int count = cookieStoreService.deleteByTarget(targetName);
        Map<String, Object> resp = new HashMap<>();
        resp.put("deleted", count);
        return ResponseEntity.ok(resp);
    }

    /** 导出为 JSON */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> export() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("cookies", cookieStoreService.getAll());
        return ResponseEntity.ok(resp);
    }
}
