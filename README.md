# selenium-tool (改版)

> **原项目**: [wangjinjing1/selenium-tool](https://github.com/wangjinjing1/selenium-tool)

## 版本记录

| 版本 | 更新内容 |
|------|---------|
| v1.11.0 | 精简重构：去掉本地 Selenium 依赖，JAR 从 50MB 降至 ~15MB；Dockerfile 直接下载预构建 JAR |
| v1.10.2 | Dockerfile 直接下载预构建 JAR，不再本地编译 |
| v1.10.0 | GitHub Actions 自动构建推送镜像 |
| v1.9.0 | compose 改用 Git URL 构建 |
| v1.8.0 | 架构重构：拆分 Selenium 和应用为两个容器 |
| v1.1.0 | 新增 Cookie 可视化管理界面 |

## 功能

- Cookie 可视化管理（Web 界面增删改查）
- 配置检查（校验 targets、Cookie、CookieCloud、定时任务）
- CookieCloud + 手动 Cookie 合并注入
- 定时任务 + webhook 通知
- Docker Compose 一键部署

## 部署

```bash
git clone https://github.com/pidanking/selenium-tool.git
cd selenium-tool
vim config/custom.yml
docker compose up -d --build
```

访问 `http://你的IP:25464` 进入管理界面。

---

# 以下为原项目 README

---
