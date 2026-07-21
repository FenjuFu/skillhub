---
title: FAQ
sidebar_position: 1
description: Frequently asked questions
---

# FAQ

## Deployment Related

### How to change default port?

Modify port configuration in `.env.release`.

### How to configure HTTPS?

Recommended to use reverse proxy (Nginx/Ingress) for TLS termination.

### How to backup database?

Use PostgreSQL standard backup tools (pg_dump).

### The page loads, but login/register APIs return 502?

The page is served by the `web` container, while login, register and other APIs are proxied from `web` to `server`
(default `SKILLHUB_API_UPSTREAM=http://server:8080`). So "page works but API returns 502" almost always means
`server` failed to start — it is rarely a frontend image problem.

Check in this order:

```bash
# 1. Is server actually running?
docker compose --env-file .env.release -f compose.release.yml ps

# 2. Read the first error in the server startup log
docker compose --env-file .env.release -f compose.release.yml logs server | head -50
```

The most common startup failure is:

```
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET must not use the default placeholder
```

It means `server` still reads the placeholder value from the template. Set your own long random string in
`.env.release` and recreate the container:

```bash
SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=<replace-with-your-own-long-random-string>
```

Running `make validate-release-config` before startup validates `.env.release` and surfaces placeholder or
missing values early.

### Why do my configuration changes have no effect?

Two frequent causes:

1. **Edited the wrong file**: `.env.release.example` is only a template. Compose reads the file passed via
   `--env-file`, which is `.env.release`. Run `cp .env.release.example .env.release` first, then edit `.env.release`.
2. **Restarted instead of recreated**: environment variables are injected when the container is created, so
   `restart` does not pick up new values. Recreate the containers after changing configuration:

```bash
docker compose --env-file .env.release -f compose.release.yml up -d --force-recreate
```

### Built-in skill sync fails in an offline / intranet environment?

A warning such as `published=0 ... failed=N` means the container cannot reach the CDN domain behind the cloud
manifest, but **the service itself is usually up**. Two options:

- With network access: allow the CDN domain shown in the log.
- Without network access: disable built-in skill sync in `.env.release` so the service starts cleanly.

```bash
SKILLHUB_BUILTIN_SKILLS_ENABLED=false
```

### How to upgrade to a new version? Does the database schema need manual changes?

With the release Compose setup, upgrading only means switching the image tag and recreating the containers.
The database schema is migrated automatically by Flyway when the new image starts — **no manual DDL is needed** —
as long as you **keep the existing data volume**.

```bash
# 1. Change the version in .env.release
SKILLHUB_VERSION=vX.Y.Z

# 2. Pull the new images and recreate
docker compose --env-file .env.release -f compose.release.yml pull
docker compose --env-file .env.release -f compose.release.yml up -d
```

Back up the database (`pg_dump`) before upgrading, and read the Breaking Changes section of the target release notes.

### Which external dependencies are required? Is MySQL supported?

The runtime depends on PostgreSQL, Redis and S3-compatible object storage. **MySQL is not supported.**
The release Compose file already ships PostgreSQL and Redis, bound to `127.0.0.1` by default.

## Usage Related

### How to reset admin password?

If you forgot admin password, you can reconfigure bootstrap admin via environment variables or directly operate the database.

### How does an OAuth (GitHub / GitLab) account become an administrator?

An account created by a first OAuth login is a regular user; an existing administrator must grant it a platform role:

1. Enable the bootstrap admin: set `BOOTSTRAP_ADMIN_ENABLED=true` in `.env.release` and check
   `BOOTSTRAP_ADMIN_USERNAME` (default `admin`) and `BOOTSTRAP_ADMIN_PASSWORD` (default `ChangeMe!2026`,
   must be changed in production).
2. Sign in as the bootstrap admin and grant `SUPER_ADMIN` to your OAuth account in user management.
3. Once the role handover is done, you can set `BOOTSTRAP_ADMIN_ENABLED` back to `false`.

Note: `USER_ADMIN` can only assign regular roles and **cannot assign or overwrite `SUPER_ADMIN`**. This is an
intentional boundary that prevents privilege escalation.

### Skill package upload failed?

Check:
1. Whether file size exceeds limit
2. Whether file type is in whitelist
3. Whether required SKILL.md is included
4. Whether SKILL.md frontmatter format is correct

### How to tell the CLI version from the server version?

They are released independently — please report both when filing an issue:

```bash
# CLI version
skillhub version

# Server version (the image tag actually running)
docker compose --env-file .env.release -f compose.release.yml images
```

New CLI capabilities usually require a server at or above the matching version. If a CLI command exists but the
server rejects it, check the server image tag first.

### How to install skills into a specific directory on an intranet?

The `install` command accepts `--dir` for the target directory, which can be scripted for batch installs:

```bash
skillhub install <skill-slug> --dir <target-path>
```

Since `v0.2.12`, public skills support anonymous search and install. Note that an invalid bearer token now fails
closed instead of falling back to anonymous access — refresh the credential or drop the invalid token.

## Development Related

### How to extend OAuth Provider?

Refer to existing GitHub implementation, add new OAuth Provider configuration.

### How to customize search implementation?

Implement `SearchIndexService` and `SearchQueryService` interfaces.

## Next Steps

- [Troubleshooting](./troubleshooting) - Problem diagnosis
