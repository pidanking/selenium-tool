# selenium-tool (改版)

> **原项目**: [wangjinjing1/selenium-tool](https://github.com/wangjinjing1/selenium-tool)
> 本仓库基于原项目进行功能扩展，保持与上游兼容。

## 版本记录

| 版本 | 更新内容 |
|------|---------|
| v1.7.0 | 重命名为 compose.yaml（Docker 新标准）；新增部署诊断脚本 check.sh |
| v1.6.0 | 绿联 NAS 兼容修复：简化 Dockerfile、entrypoint 脚本、.gitattributes 换行符规范化、去掉 image 字段 |
| v1.5.0 | compose image 标签同步最新版本号 |
| v1.4.0 | 修复 Docker Compose 部署问题：去掉 image 拉取、显式指定 build context 和 Dockerfile、安装 curl 健康检查依赖 |
| v1.3.0 | 新增配置检查功能：校验 targets、Cookie 格式、CookieCloud 连通性、定时任务合规性；首页和 Cookie 管理页均支持一键检查 |
| v1.2.0 | Docker Compose 部署优化；数据持久化卷；健康检查；资源限制；.dockerignore；config 示例独立目录 |
| v1.1.0 | 新增 Cookie 可视化管理界面；支持手动添加/编辑/删除 Cookie；执行时自动合并手动 Cookie + CookieCloud Cookie |
| v1.0.0 | 基于原项目初始版本 |

## 改版说明

本项目在原版基础上新增了以下功能：

- **Cookie 可视化管理**：通过 Web 界面管理各站点的 Cookie，无需手动编辑 YAML
  - 按 target 分组展示 Cookie
  - 支持增删改查操作
  - Cookie 持久化到 `cookie-store.json`
- **Cookie 合并注入**：执行任务时自动合并手动配置的 Cookie 和 CookieCloud 拉取的 Cookie（手动优先）

## Docker Compose 部署

```bash
# 1. 克隆仓库
git clone https://github.com/pidanking/selenium-tool.git
cd selenium-tool

# 2. 编辑配置
cp config/custom.yml config/custom.yml.bak
vim config/custom.yml   # 填入你的站点、CookieCloud、webhook 等配置

# 3. 启动
docker compose up -d --build

# 4. 查看日志
docker compose logs -f
```

### 目录结构

```
selenium-tool/
├── config/
│   └── custom.yml          # 个性化配置（挂载到容器，只读）
├── data/
│   ├── cookie-store.json   # 手动配置的 Cookie（持久化）
│   └── cookiecloud-cache.json  # CookieCloud 缓存（持久化）
├── docker-compose.yml
└── Dockerfile
```

### 端口说明

| 端口 | 用途 |
|------|------|
| 8080 | Web 管理界面 + API |
| 7900 | noVNC 远程桌面（可选） |

---

# 以下为原项目 README

---

基于 Java、Spring Boot、Selenium 的定时浏览器任务工具，支持从 CookieCloud 拉取 Cookie，并通过 webhook 发送启动结果和异常告警。

## 功能概览

- 支持 Spring Boot 定时任务，按 `cron` 自动执行
- 支持 Selenium + Chrome/Chromium 自动打开页面、注入 Cookie、注入 localStorage、停留后退出
- 支持启动后立即执行一次任务
- 支持 CookieCloud 拉取 Cookie，并带本地缓存回退
- 支持通过 webhook 发送启动成功、启动失败、浏览器关闭等通知
- 支持 Docker 运行，默认直接使用 Jar 内置配置
- 提供手动触发接口 `POST /api/automation/run`
- 支持通过 Chrome prefs 或 CDP 阻止指定资源加载，减少带宽消耗

## 目录说明

- `src/main/resources/application.yml`
  项目基础配置，包含浏览器、定时任务等通用参数
- `src/main/resources/custom.yml`
  个性化配置，包含目标站点、CookieCloud、webhook、代理等，`JAR` 同级目录放置同名文件可覆盖默认值
- `Dockerfile`
  构建运行镜像，基于 `selenium/standalone-chrome`
- `docker-compose.yml`
  本地快速启动容器
- `cookiecloud-cache.json`
  CookieCloud 本地缓存文件

## 配置说明

项目采用双文件分层配置策略：

| 文件 | 用途 |
|------|------|
| `application.yml` | 浏览器、定时任务等基础参数，一般不需要改动 |
| `custom.yml` | 目标站点、CookieCloud、webhook、代理等个性化参数，按需修改 |

`custom.yml` 中的值会覆盖 `application.yml` 中相同的 key。Docker 部署时，可将宿主机的 `custom.yml` 挂载到 `/app/config/custom.yml` 实现外部化配置。

### `automation.run-on-startup`

- `true` 表示应用启动后立即执行一次自动化任务
- `false` 表示只等定时任务或手动触发

### `automation.schedule`

- `cron` 使用 Spring 六段式表达式
- `zone` 指定时区，例如 `Asia/Shanghai`

```yaml
schedule:
  cron: "0 0 9 * * *"   # 每天上午 9 点执行一次
  zone: "Asia/Shanghai"
```

### `automation.browser`

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `headless` | `true` | 无界面运行；本地调试可设为 `false` |
| `no-sandbox` | `true` | Linux / Docker 环境建议开启 |
| `disable-dev-shm-usage` | `true` | Docker 共享内存较小时建议开启 |
| `page-stay-seconds` | `10` | 页面访问成功后的停留秒数 |
| `page-load-timeout` | `1m` | 页面加载超时时间 |
| `binary-path` | 空 | 浏览器可执行文件路径；路径特殊时需显式配置 |
| `driver-path` | 空 | ChromeDriver 路径；不填由 Selenium Manager 自动下载 |
| `proxy` | 空 | 代理地址，例如 `http://127.0.0.1:7897`；不填时自动检测系统代理 |
| `arguments` | `[]` | 额外浏览器启动参数列表 |
| `disable-images` | `true` | 通过 Chrome prefs 在浏览器层面完全禁止图片加载 |
| `block-resource-suffixes` | `[]` | 通过 CDP 按后缀阻止资源加载，列表为空则不启用 |

**资源拦截说明：**

`disable-images` 和 `block-resource-suffixes` 是两种独立机制，可按需选择或同时使用：

- `disable-images: true`：Chrome 内核级别禁止所有图片，效果彻底，无法按格式区分
- `block-resource-suffixes`：CDP 网络层按文件后缀拦截，可精细控制，支持任意后缀，例如：

```yaml
block-resource-suffixes:
  - "gif"
  - "mp4"
  - "woff2"
```

### `automation.cookie-cloud`

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `url` | 空 | CookieCloud 服务根地址；**未配置时 CookieCloud 功能自动禁用** |
| `key` | 空 | CookieCloud 访问 key |
| `password` | 空 | CookieCloud 访问密码 |
| `cache-file` | `cookiecloud-cache.json` | 本地缓存文件路径 |
| `allow-cache-fallback` | `true` | 远端获取失败时是否允许回退到本地缓存 |

> `url` 配置了有效值则自动启用，无需额外的 `enabled` 字段。代码会自动在 `url` 后拼接 `/cookiecloud/get/{key}`，无需手动填写完整路径。

CookieCloud 当前行为：

- 优先请求远端 CookieCloud
- 远端成功时刷新本地缓存
- 远端失败且 `allow-cache-fallback=true` 时，回退到本地缓存继续执行
- 远端失败且 `allow-cache-fallback=false` 时，直接抛出异常中止任务
- CookieCloud 调不通时记录日志，并在启动成功 webhook 中附带告警信息

### `automation.startup-notification`

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `webhook-url` | 空 | webhook 地址；**未配置时通知功能自动禁用** |
| `message` | `selenium-tool启动成功` | 启动成功时的主消息内容 |

> `webhook-url` 配置了有效值则自动启用，无需额外的 `enabled` 字段。

当前通知策略：

- 应用启动成功后发送 webhook
- 如果 CookieCloud 调不通，会在 webhook 中追加告警
- 如果目标站点状态检查失败，也会在 webhook 中追加失败摘要
- 应用启动阶段抛异常时，会尝试发送启动失败通知
- 浏览器在运行中被手动关闭，会发送告警 webhook 并记录日志

### `automation.targets`

每个 target 表示一个需要访问的目标页面，按配置顺序依次执行。

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `name` | 自动取 URL host | 任务名称，用于日志识别 |
| `url` | 必填 | 实际访问页面地址 |
| `cookie-domain` | 空 | Cookie 过滤域名；为空时从 `url` 自动提取 |
| `warmup-path` | `/favicon.ico` | 注入 Cookie 前先访问的预热路径 |
| `local-storage` | `[]` | 需要注入的 localStorage 键值对列表 |

`local-storage` 示例：

```yaml
targets:
  - url: "https://example.com/"
    local-storage:
      - key: "token"
        value: "your-token-value"
```

## 配置示例

### `application.yml`（基础配置，通常不需要改动）

```yaml
automation:
  run-on-startup: true
  schedule:
    zone: "Asia/Shanghai"
  browser:
    headless: false
    no-sandbox: true
    disable-dev-shm-usage: true
    page-stay-seconds: 3
    page-load-timeout: 2m
    arguments:
      - "--window-size=1400,900"
      - "--disable-gpu"
    disable-images: true
    block-resource-suffixes:
  cookie-cloud:
    cache-file: "cookiecloud-cache.json"
```

### `custom.yml`（个性化配置，按需修改）

```yaml
automation:
  run-on-startup: true
  schedule:
    cron: "0 0 10 * * *"   # 每天上午 10 点执行一次
  browser:
    # 代理地址，国内环境下载 chromedriver 时使用；不需要代理可留空
    proxy:
  cookie-cloud:
    # CookieCloud 服务根地址，代码自动拼接 /cookiecloud/get/{key}
    url: "https://your-cookiecloud-host"
    key: "your-key"
    password: "your-password"
  startup-notification:
    # 企业微信 / 飞书 / 自定义 webhook 地址，留空则不发通知
    webhook-url:
  targets:
    - url: "https://example.com/"
      cookie-domain: "example.com"
```

## 本地运行

要求：

- JDK 17
- Maven 3.9+
- 本机已安装 Chrome 或 Chromium

启动：

```bash
mvn spring-boot:run
```

或者直接运行：

```text
com.example.seleniumtool.SeleniumToolApplication
```

## Docker 运行

### 构建镜像

```bash
docker build -t selenium-tool .
```

### 启动容器

```bash
docker run -d \
  --name selenium-tool \
  -e TZ=Asia/Shanghai \
  -p 7900:7900 \
  selenium-tool
```

或者：

```bash
docker compose up -d --build
```

说明：

- 镜像基于 `selenium/standalone-chrome`
- 容器启动时会先拉起基础镜像自带的 Selenium / noVNC 服务，再启动 Java 应用
- Docker 默认直接使用 Jar 内的配置文件

### 覆盖默认配置

将宿主机的 `custom.yml` 挂载到容器 `/app/config/custom.yml`，可在不重新构建镜像的情况下覆盖个性化配置：

```bash
docker run -d \
  --name selenium-tool \
  -e TZ=Asia/Shanghai \
  -v $(pwd)/config/custom.yml:/app/config/custom.yml:ro \
  -p 7900:7900 \
  selenium-tool
```

## noVNC / VNC

基础镜像 `selenium/standalone-chrome` 默认提供 VNC / noVNC 能力。

- noVNC：`http://localhost:7900`

如需更多 VNC 参数，按 `selenium/standalone-chrome` 官方环境变量配置即可。

## 手动触发

```bash
curl -X POST http://localhost:8080/api/automation/run
```

## 启动与失败行为

启动流程：

1. Spring Boot 启动
2. 如 `run-on-startup=true`，执行一次自动化任务
3. 汇总 CookieCloud 告警和目标检查失败信息
4. 如配置了 `webhook-url`，发送启动成功通知

失败处理规则：

- `CookieCloud` 不通：记日志，不阻断启动，webhook 提示用户
- `webhook` 发送失败：记日志，不阻断启动
- 浏览器在运行中被手动关闭：发送告警 webhook，并在日志中记录

## 开发建议

- 本地调试优先关掉定时任务，只保留手动触发
- Docker 环境建议保留 `no-sandbox` 和 `disable-dev-shm-usage`
- 如果目标站点依赖登录态，优先确认 CookieCloud 返回结构和域名过滤结果
- `driver-path` 未配置时由 Selenium Manager 自动下载 ChromeDriver，国内网络环境下可能较慢，建议配置 `proxy` 或手动下载后填写 `driver-path`
