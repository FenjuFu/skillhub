---
title: Troubleshooting
sidebar_position: 2
description: Common problem diagnosis and solutions
---

# Troubleshooting

## Service Cannot Start

### Checklist

1. Check container status: `docker compose ps`
2. View service logs: `docker compose logs <service>`
3. Verify environment variables: Check `.env.release` configuration
4. Check port occupancy: `netstat -tlnp`

### Common Causes

- Port occupied
- Database connection failed
- Redis connection failed
- Environment variables missing

### PostgreSQL container fails to start with `operation not permitted` (cannot write `postmaster.pid` / `pg_wal`)

Common in intranet / self-hosted environments using bind mounts. The root cause is a mismatch between the **data volume directory permissions** and the container's `postgres` user (UID 999):

1. Change the data volume directory owner to the postgres user: `chown -R 999:999 <data-dir>`.
2. On RHEL/CentOS, check whether SELinux is blocking the container from writing to the host directory.
3. Prefer the official `runtime.sh` deployment script, which handles the relevant initialization steps and avoids permission gaps from hand-written compose files.

## Upload Failed

### Skill Package Upload Failed

1. Check file size
2. Check file type
3. Check SKILL.md format
4. View server logs

## Authentication Issues

### Cannot Login

1. Check OAuth configuration
2. Check callback URL configuration
3. Check `SKILLHUB_PUBLIC_BASE_URL` configuration

## Performance Issues

### Slow Search

1. Check PostgreSQL full-text index
2. Consider upgrading to Elasticsearch (future version)

### Slow Download

1. Check object storage configuration
2. Check network bandwidth

## Get Help

If above solutions cannot resolve the issue:
1. View logs
2. Submit Issue
3. Contact technical support

## Next Steps

- [Changelog](./changelog) - Version history
