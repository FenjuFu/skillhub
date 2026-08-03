# SkillHub Helm Chart

A values-driven Helm chart for deploying [SkillHub](https://github.com/iflytek/skillhub)
to Kubernetes. It packages the same topology as the Kustomize manifests under
[`deploy/k8s`](../../k8s) — backend, frontend, scanner, plus optional in-cluster
PostgreSQL and Redis — behind a single `values.yaml`.

## Prerequisites

- Kubernetes v1.24+
- Helm v3.8+
- An ingress controller (nginx by default) if you want host-based access
- A default StorageClass (for PVCs), unless you disable persistence

## Quick start

```bash
# From the repository root
helm install skillhub ./deploy/helm/skillhub \
  --namespace skillhub --create-namespace
```

This brings up the full stack with an in-cluster PostgreSQL and Redis.

> ⚠️ The defaults ship demo credentials (`admin` / `ChangeMe!2026`, DB password
> `change-me`). Override them before any real deployment.

## Common configurations

**Set a hostname and strong secrets:**

```bash
helm install skillhub ./deploy/helm/skillhub \
  --namespace skillhub --create-namespace \
  --set ingress.host=skills.example.com \
  --set session.cookieSecure=true \
  --set bootstrapAdmin.password='<strong-password>' \
  --set postgresql.auth.password='<strong-db-password>'
```

**Use an external PostgreSQL and Redis:**

```bash
helm install skillhub ./deploy/helm/skillhub \
  --namespace skillhub --create-namespace \
  --set postgresql.enabled=false \
  --set externalDatabase.url='jdbc:postgresql://db.internal:5432/skillhub' \
  --set externalDatabase.username=skillhub \
  --set externalDatabase.password='<db-password>' \
  --set redis.enabled=false \
  --set externalRedis.host=redis.internal
```

**Use S3 / OSS object storage instead of a PVC:**

```bash
helm install skillhub ./deploy/helm/skillhub \
  --namespace skillhub --create-namespace \
  --set storage.provider=s3 \
  --set storage.s3.endpoint=https://oss-cn-shanghai.aliyuncs.com \
  --set storage.s3.bucket=skillhub-prod \
  --set storage.s3.region=cn-shanghai \
  --set storage.s3.accessKey='<ak>' \
  --set storage.s3.secretKey='<sk>'
```

**Bring your own Secret** (recommended for production — keeps credentials out of
Helm values/history). Create a Secret with the keys listed in
[`templates/secret.yaml`](./templates/secret.yaml), then:

```bash
helm install skillhub ./deploy/helm/skillhub \
  --set existingSecret=my-skillhub-secret
```

## Key values

| Key | Default | Description |
|-----|---------|-------------|
| `image.registry` | `ghcr.io/iflytek` | Registry/namespace for all images |
| `image.tag` | `latest` | Default tag for all components |
| `imagePullSecrets` | `[]` | Pull secrets for private images |
| `server.replicas` / `web.replicas` | `1` | Replica counts |
| `scanner.enabled` | `true` | Deploy the security scanner |
| `storage.provider` | `local` | `local` (PVC) or `s3` |
| `storage.persistence.size` | `10Gi` | Skill storage PVC size (local) |
| `session.cookieSecure` | `false` | Set `true` behind HTTPS |
| `bootstrapAdmin.username` | `admin` | Initial admin user |
| `bootstrapAdmin.password` | `ChangeMe!2026` | **Change this** |
| `postgresql.enabled` | `true` | In-cluster PostgreSQL |
| `postgresql.auth.password` | `change-me` | **Change this** |
| `externalDatabase.url` | `""` | JDBC URL when `postgresql.enabled=false` |
| `redis.enabled` | `true` | In-cluster Redis |
| `externalRedis.host` | `""` | Redis host when `redis.enabled=false` |
| `ingress.enabled` | `true` | Create an Ingress |
| `ingress.host` | `skills.example.com` | Ingress hostname |
| `existingSecret` | `""` | Use a pre-created Secret instead of rendering one |

See [`values.yaml`](./values.yaml) for the complete list.

## Validate the rendered manifests

```bash
helm lint ./deploy/helm/skillhub
helm template skillhub ./deploy/helm/skillhub | kubectl apply --dry-run=client -f -
```

## Uninstall

```bash
helm uninstall skillhub --namespace skillhub
```

PersistentVolumeClaims created by the StatefulSets are retained by default —
delete them manually if you want to reclaim storage.

## Relationship to the Kustomize manifests

The Kustomize manifests under [`deploy/k8s`](../../k8s) remain the reference,
hand-editable form. This chart mirrors them and is aimed at users who prefer
values-driven configuration and versioned Helm releases. The two are kept in
sync intentionally.
