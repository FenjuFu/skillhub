#!/bin/sh
set -eu

# Real container smoke test: runs the actual nginx:alpine entrypoint with the
# repo's nginx template + 20-base-path.sh, then verifies over HTTP that a
# sub-path deployment serves real assets (not the SPA fallback) and redirects
# the bare prefix. This catches routing regressions that a text-only check
# cannot (e.g. assets falling through to index.html).

if ! command -v docker >/dev/null 2>&1; then
  printf '%s\n' 'web-base-path-nginx-smoke-test skipped (docker unavailable)'
  exit 0
fi

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
NGINX_IMAGE="${NGINX_SMOKE_IMAGE:-nginx:alpine}"
name="skillhub-base-path-smoke-$$"
port=18080

tmp=$(mktemp -d)
cleanup() {
  docker rm -f "$name" "$name-fixed" >/dev/null 2>&1 || true
  rm -rf "$tmp"
}
trap cleanup EXIT

html="$tmp/html"
mkdir -p "$html/assets" "$html/install" "$html/registry"
printf '%s\n' 'INDEX_HTML_MARKER' >"$html/index.html"
printf '%s\n' 'APP_JS_MARKER' >"$html/assets/app.js"
cp "$ROOT_DIR/web/src/docs/skill.md.template" "$html/registry/skill.md.template"
cp "$ROOT_DIR/web/runtime-config.js.template" "$html/runtime-config.js.template"

# The image build chmods the entrypoint scripts; here we mount a copy and make it
# executable, since the nginx entrypoint silently ignores non-executable *.sh.
entrypoint_d="$tmp/entrypoint.d"
mkdir -p "$entrypoint_d"
cp "$ROOT_DIR/web/docker-entrypoint.d/20-base-path.sh" "$entrypoint_d/20-base-path.sh"
cp "$ROOT_DIR/web/docker-entrypoint.d/30-runtime-config.sh" "$entrypoint_d/30-runtime-config.sh"
chmod +x "$entrypoint_d/20-base-path.sh" "$entrypoint_d/30-runtime-config.sh"

if ! docker run -d --name "$name" \
    -p "$port:80" \
    -e SKILLHUB_API_UPSTREAM=http://127.0.0.1:9 \
    -e SKILLHUB_TRUST_FORWARDED_PROTO=false \
    -e SKILLHUB_WEB_BASE_PATH=/skillhub/ \
    -e SKILLHUB_PUBLIC_BASE_URL=https://skill.example.com/skillhub \
    -v "$html:/usr/share/nginx/html" \
    -v "$ROOT_DIR/web/nginx.conf.template:/etc/nginx/templates/default.conf.template:ro" \
    -v "$entrypoint_d/20-base-path.sh:/docker-entrypoint.d/20-base-path.sh:ro" \
    -v "$entrypoint_d/30-runtime-config.sh:/docker-entrypoint.d/30-runtime-config.sh:ro" \
    "$NGINX_IMAGE" >/dev/null 2>&1; then
  printf '%s\n' 'web-base-path-nginx-smoke-test skipped (docker run failed, e.g. no image/network)'
  exit 0
fi

base="http://127.0.0.1:$port"
ready=0
i=0
while [ "$i" -lt 30 ]; do
  if curl -fsS -o /dev/null "$base/nginx-health" 2>/dev/null; then
    ready=1
    break
  fi
  i=$((i + 1))
  sleep 1
done
if [ "$ready" -ne 1 ]; then
  echo 'nginx did not become ready' >&2
  docker logs "$name" >&2 || true
  exit 1
fi

# Asset under the sub-path must serve the real file, not the SPA fallback.
asset=$(curl -fsS "$base/skillhub/assets/app.js")
if [ "$asset" != 'APP_JS_MARKER' ]; then
  echo "sub-path asset must serve the real file, got: $asset" >&2
  exit 1
fi

# App route under the sub-path falls back to index.html (SPA).
index=$(curl -fsS "$base/skillhub/dashboard")
if [ "$index" != 'INDEX_HTML_MARKER' ]; then
  echo "sub-path SPA route must serve index.html, got: $index" >&2
  exit 1
fi

# Bare prefix redirects to the trailing-slash form.
code=$(curl -s -o /dev/null -w '%{http_code}' "$base/skillhub")
if [ "$code" != '301' ]; then
  echo "bare prefix must 301-redirect, got: $code" >&2
  exit 1
fi
location=$(curl -s -o /dev/null -D - "$base/skillhub" | awk 'tolower($1) == "location:" { print $2 }' | tr -d '\r')
if [ "$location" != '/skillhub/' ]; then
  echo "bare prefix redirect must stay relative to preserve an upstream HTTPS scheme, got: $location" >&2
  exit 1
fi

# The preferred Agent install guide is generated from the instance URL and is
# reachable through the configured sub-path. The legacy registry route remains
# available from the same source document.
guide=$(curl -fsS "$base/skillhub/install/skillhub.md")
printf '%s' "$guide" | grep -F 'The primary registry for this guide is `https://skill.example.com/skillhub`.' >/dev/null
printf '%s' "$guide" | grep -F 'read the sibling `.skillhub/metadata.json` first' >/dev/null
printf '%s' "$guide" | grep -F 'skillhub list --agent <agent> --registry https://skill.example.com/skillhub --json' >/dev/null
printf '%s' "$guide" | grep -F 'skillhub install @global/skillhub-registry' >/dev/null
printf '%s' "$guide" | grep -F 'skillhub upgrade @global/skillhub-registry \' >/dev/null
printf '%s' "$guide" | grep -F 'skillhub search "<query>" --registry https://skill.xfyun.cn --json' >/dev/null
printf '%s' "$guide" | grep -F 'npx --yes clawhub search "<query>"' >/dev/null
printf '%s' "$guide" | grep -F 'skillhub login --token <token> --registry https://skill.example.com/skillhub' >/dev/null
legacy_guide=$(curl -fsS "$base/skillhub/registry/skill.md")
if [ "$guide" != "$legacy_guide" ]; then
  echo 'preferred and compatibility Agent guides must have identical content' >&2
  exit 1
fi
cache_control=$(curl -sSI "$base/skillhub/install/skillhub.md" | awk -F': ' 'tolower($1) == "cache-control" { print $2 }' | tr -d '\r')
if [ "$cache_control" != 'no-cache' ]; then
  echo "Agent guide must be revalidated instead of cached indefinitely, got: $cache_control" >&2
  exit 1
fi

docker rm -f "$name" >/dev/null 2>&1 || true

# Fixed-base image served via the bundled deploy configs: assets are baked under
# /fixed/, a baked-base marker is present, and SKILLHUB_WEB_BASE_PATH is passed as
# an empty string (as compose.release.yml / k8s do). Routing must follow the baked
# base, not fall back to root. Reproduces the reported P1 regression.
fixed_html="$tmp/fixed-html"
mkdir -p "$fixed_html/assets"
printf '%s\n' 'INDEX_HTML_MARKER' >"$fixed_html/index.html"
printf '%s\n' 'FIXED_APP_JS_MARKER' >"$fixed_html/assets/app.js"
baked_file="$tmp/baked-base-path"
printf '%s' '/fixed/' >"$baked_file"
fixed_name="$name-fixed"
fixed_port=18081

docker run -d --name "$fixed_name" \
  -p "$fixed_port:80" \
  -e SKILLHUB_API_UPSTREAM=http://127.0.0.1:9 \
  -e SKILLHUB_TRUST_FORWARDED_PROTO=false \
  -e SKILLHUB_WEB_BASE_PATH= \
  -e SKILLHUB_WEB_BAKED_BASE_PATH_FILE=/etc/skillhub/baked-base-path \
  -v "$fixed_html:/usr/share/nginx/html:ro" \
  -v "$baked_file:/etc/skillhub/baked-base-path:ro" \
  -v "$ROOT_DIR/web/nginx.conf.template:/etc/nginx/templates/default.conf.template:ro" \
  -v "$entrypoint_d/20-base-path.sh:/docker-entrypoint.d/20-base-path.sh:ro" \
  "$NGINX_IMAGE" >/dev/null 2>&1

fixed_base="http://127.0.0.1:$fixed_port"
ready=0
i=0
while [ "$i" -lt 30 ]; do
  if curl -fsS -o /dev/null "$fixed_base/nginx-health" 2>/dev/null; then
    ready=1
    break
  fi
  i=$((i + 1))
  sleep 1
done
if [ "$ready" -ne 1 ]; then
  echo 'nginx (fixed-base) did not become ready' >&2
  docker logs "$fixed_name" >&2 || true
  exit 1
fi

fixed_asset=$(curl -fsS "$fixed_base/fixed/assets/app.js")
if [ "$fixed_asset" != 'FIXED_APP_JS_MARKER' ]; then
  echo "fixed-base asset must serve the real file, got: $fixed_asset" >&2
  exit 1
fi

printf '%s\n' 'web-base-path-nginx-smoke-test passed'
