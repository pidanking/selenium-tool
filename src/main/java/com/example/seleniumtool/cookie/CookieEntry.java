package com.example.seleniumtool.cookie;

import lombok.Data;

/**
 * 手动配置的 Cookie 条目。
 */
@Data
public class CookieEntry {

    private String id;
    /** 所属目标名称（对应 target.name） */
    private String targetName;
    private String domain;
    private String name;
    private String value;
    private String path = "/";
    private boolean secure;
    private boolean httpOnly;
    /** 过期时间，ISO-8601 格式，为空表示会话 Cookie */
    private String expires;
    /** 备注 */
    private String remark;
}
