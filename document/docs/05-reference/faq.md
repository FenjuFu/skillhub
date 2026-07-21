---
title: 常见问题
sidebar_position: 1
description: 常见问题解答
---

# 常见问题

## 部署相关

### 如何修改默认端口？

修改 `.env.release` 中的端口配置。

### 如何配置 HTTPS？

建议使用反向代理（Nginx/Ingress）处理 TLS 终止。

### 数据库如何备份？

使用 PostgreSQL 标准备份工具（pg_dump）。

### 页面能打开，但登录/注册接口返回 502？

页面由 `web` 容器提供，登录、注册等接口由 `web` 转发给 `server`（默认 `SKILLHUB_API_UPSTREAM=http://server:8080`）。
因此「页面正常但 API 502」基本都是 `server` 没有正常启动，而不是前端镜像的问题。

排查顺序：

```bash
# 1. 看 server 是否处于运行状态
docker compose --env-file .env.release -f compose.release.yml ps

# 2. 看 server 启动日志中的第一条错误
docker compose --env-file .env.release -f compose.release.yml logs server | head -50
```

最常见的一条启动失败日志是：

```
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET must not use the default placeholder
```

说明 `server` 读到的仍是模板里的占位值。在 `.env.release` 中改成自己的随机长字符串后重建容器即可：

```bash
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=<替换成你自己的随机长字符串>
```

启动前可以先执行 `make validate-release-config`，它会在启动前校验 `.env.release`，提前暴露这类占位值和缺失项。

### 改了配置为什么不生效？

两个高频原因：

1. **改错了文件**：`.env.release.example` 只是模板，Compose 实际读取的是 `--env-file` 指定的 `.env.release`。请先 `cp .env.release.example .env.release`，然后改 `.env.release`。
2. **只重启没重建**：环境变量在容器创建时注入，`restart` 不会重新注入。改完配置需要重建容器：

```bash
docker compose --env-file .env.release -f compose.release.yml up -d --force-recreate
```

### 离线 / 内网环境启动时内置技能同步失败怎么办？

日志出现 `published=0 ... failed=N` 之类的内置技能同步告警时，说明容器访问不到云端 manifest 对应的 CDN 域名，
但**服务本身通常已经起来了**。两种处理方式：

- 能联网：放通日志中提示的 CDN 域名。
- 不能联网：在 `.env.release` 中关闭内置技能同步，先让服务正常启动。

```bash
SKILLHUB_BUILTIN_SKILLS_ENABLED=false
```

### 如何升级到新版本？数据库结构需要手工改吗？

按发布版 Compose 部署时，升级只需要换镜像标签并重建容器；数据库结构由 Flyway 在新镜像启动时自动迁移，**不需要手工改表**，
前提是**保留原有数据卷**。

```bash
# 1. 在 .env.release 中修改版本
SKILLHUB_VERSION=vX.Y.Z

# 2. 拉取新镜像并重建
docker compose --env-file .env.release -f compose.release.yml pull
docker compose --env-file .env.release -f compose.release.yml up -d
```

升级前建议先备份数据库（`pg_dump`），并阅读目标版本 Release Notes 中的 Breaking Changes 一节。

### 需要哪些外部依赖？支持 MySQL 吗？

运行时依赖 PostgreSQL、Redis 和 S3 兼容对象存储，目前**不支持 MySQL**。
发布版 Compose 已经内置 PostgreSQL 与 Redis，默认只绑定在 `127.0.0.1`。

## 使用相关

### 如何重置管理员密码？

如果忘记管理员密码，可通过环境变量重新设置首登管理员，或直接操作数据库。

### 通过 OAuth（GitHub / GitLab 等）登录的账号，如何取得管理员权限？

OAuth 首次登录的账号默认是普通用户，需要由一个已有管理员为其授予平台角色：

1. 启用首登管理员：`.env.release` 中设置 `BOOTSTRAP_ADMIN_ENABLED=true`，并确认 `BOOTSTRAP_ADMIN_USERNAME`（默认 `admin`）、`BOOTSTRAP_ADMIN_PASSWORD`（默认 `ChangeMe!2026`，生产环境必须改）。
2. 用首登管理员登录，在用户管理中给你的 OAuth 账号授予 `SUPER_ADMIN`。
3. 权限体系完成交接后，可将 `BOOTSTRAP_ADMIN_ENABLED` 改回 `false`。

注意：`USER_ADMIN` 只能分配普通角色，**不能分配或改写 `SUPER_ADMIN`**，这是刻意的权限边界，避免越权提升。

### 技能包上传失败怎么办？

检查：
1. 文件大小是否超限
2. 文件类型是否在白名单内
3. 是否包含必需的 SKILL.md
4. SKILL.md frontmatter 格式是否正确

### 如何区分 CLI 版本和服务端版本？

两者独立发布，排查问题时建议同时提供：

```bash
# CLI 版本
skillhub version

# 服务端版本（看实际运行的镜像标签）
docker compose --env-file .env.release -f compose.release.yml images
```

CLI 的新增能力通常要求服务端不低于对应版本，出现「CLI 有这个命令但服务端报错」时，优先确认服务端镜像标签。

### 内网环境如何把技能批量安装到指定目录？

`install` 命令支持 `--dir` 指定安装目录，可脚本化批量执行：

```bash
skillhub install <skill-slug> --dir <target-path>
```

自 `v0.2.12` 起，公开技能支持匿名搜索与安装；如果配置了无效的 Bearer Token，命令会直接失败而不再回退匿名访问，
遇到这种情况请更新凭据或先移除无效 Token。

## 开发相关

### 如何扩展 OAuth Provider？

参考现有 GitHub 实现，添加新的 OAuth Provider 配置。

### 如何自定义搜索实现？

实现 `SearchIndexService` 和 `SearchQueryService` 接口。

## 下一步

- [故障排查](./troubleshooting) - 问题诊断
