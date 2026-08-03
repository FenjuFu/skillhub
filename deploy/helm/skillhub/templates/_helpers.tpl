{{/* vim: set filetype=mustache: */}}

{{/* Chart name (respecting nameOverride) */}}
{{- define "skillhub.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Fully qualified app name */}}
{{- define "skillhub.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- if contains $name .Release.Name -}}
{{- .Release.Name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{/* Chart label value */}}
{{- define "skillhub.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/* Component names */}}
{{- define "skillhub.server.fullname" -}}{{ printf "%s-server" (include "skillhub.fullname" .) }}{{- end -}}
{{- define "skillhub.web.fullname" -}}{{ printf "%s-web" (include "skillhub.fullname" .) }}{{- end -}}
{{- define "skillhub.scanner.fullname" -}}{{ printf "%s-scanner" (include "skillhub.fullname" .) }}{{- end -}}
{{- define "skillhub.postgresql.fullname" -}}{{ printf "%s-postgresql" (include "skillhub.fullname" .) }}{{- end -}}
{{- define "skillhub.redis.fullname" -}}{{ printf "%s-redis" (include "skillhub.fullname" .) }}{{- end -}}
{{- define "skillhub.configmap.fullname" -}}{{ printf "%s-config" (include "skillhub.fullname" .) }}{{- end -}}
{{- define "skillhub.secret.fullname" -}}{{ printf "%s-secret" (include "skillhub.fullname" .) }}{{- end -}}
{{- define "skillhub.storage.pvcName" -}}{{ printf "%s-storage" (include "skillhub.fullname" .) }}{{- end -}}

{{/* Effective Secret name (existingSecret takes precedence) */}}
{{- define "skillhub.secretName" -}}
{{- if .Values.existingSecret -}}{{ .Values.existingSecret }}{{- else -}}{{ include "skillhub.secret.fullname" . }}{{- end -}}
{{- end -}}

{{/* Common metadata labels */}}
{{- define "skillhub.labels" -}}
helm.sh/chart: {{ include "skillhub.chart" . }}
app.kubernetes.io/part-of: skillhub
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Build a component image reference.
Usage: {{ include "skillhub.image" (list . .Values.server) }}
*/}}
{{- define "skillhub.image" -}}
{{- $root := index . 0 -}}
{{- $comp := index . 1 -}}
{{- printf "%s/%s:%s" $root.Values.image.registry $comp.image.repository (default $root.Values.image.tag $comp.image.tag) -}}
{{- end -}}

{{/* Datasource (JDBC) resolution */}}
{{- define "skillhub.datasourceUrl" -}}
{{- if .Values.postgresql.enabled -}}
{{- printf "jdbc:postgresql://%s:5432/%s" (include "skillhub.postgresql.fullname" .) .Values.postgresql.auth.database -}}
{{- else -}}
{{- required "externalDatabase.url is required when postgresql.enabled=false" .Values.externalDatabase.url -}}
{{- end -}}
{{- end -}}

{{- define "skillhub.datasourceUsername" -}}
{{- if .Values.postgresql.enabled -}}{{ .Values.postgresql.auth.username }}{{- else -}}{{ required "externalDatabase.username is required when postgresql.enabled=false" .Values.externalDatabase.username }}{{- end -}}
{{- end -}}

{{- define "skillhub.datasourcePassword" -}}
{{- if .Values.postgresql.enabled -}}{{ .Values.postgresql.auth.password }}{{- else -}}{{ required "externalDatabase.password is required when postgresql.enabled=false" .Values.externalDatabase.password }}{{- end -}}
{{- end -}}

{{/* Redis resolution */}}
{{- define "skillhub.redisHost" -}}
{{- if .Values.redis.enabled -}}{{ include "skillhub.redis.fullname" . }}{{- else -}}{{ required "externalRedis.host is required when redis.enabled=false" .Values.externalRedis.host }}{{- end -}}
{{- end -}}

{{- define "skillhub.redisPort" -}}
{{- if .Values.redis.enabled -}}6379{{- else -}}{{ .Values.externalRedis.port | default 6379 }}{{- end -}}
{{- end -}}
