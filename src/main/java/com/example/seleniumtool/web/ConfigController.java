package com.example.seleniumtool.web;

import com.example.seleniumtool.service.ConfigValidationService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigValidationService configValidationService;

    public ConfigController(ConfigValidationService configValidationService) {
        this.configValidationService = configValidationService;
    }

    /**
     * 校验当前配置是否合规，返回每个检查项的通过/失败状态。
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate() {
        return ResponseEntity.ok(configValidationService.validate());
    }
}
