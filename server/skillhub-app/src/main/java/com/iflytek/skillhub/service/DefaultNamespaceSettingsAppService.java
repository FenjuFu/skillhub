package com.iflytek.skillhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.DefaultNamespaceBackfillReport;
import com.iflytek.skillhub.domain.namespace.DefaultNamespaceMembershipService;
import com.iflytek.skillhub.domain.namespace.DefaultNamespaceSettings;
import com.iflytek.skillhub.dto.DefaultNamespaceBackfillResponse;
import com.iflytek.skillhub.dto.DefaultNamespaceSettingsResponse;
import com.iflytek.skillhub.dto.DefaultNamespaceSettingsUpdateRequest;
import com.iflytek.skillhub.observability.RequestIdAccessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes the "namespaces every new account joins" policy to the admin console.
 */
@Service
public class DefaultNamespaceSettingsAppService {

    private static final String AUDIT_TARGET_TYPE = "SYSTEM_SETTING";
    private static final String AUDIT_ACTION_UPDATE = "SYSTEM_SETTING_DEFAULT_NAMESPACES_UPDATE";
    private static final String AUDIT_ACTION_BACKFILL = "SYSTEM_SETTING_DEFAULT_NAMESPACES_BACKFILL";

    private final DefaultNamespaceMembershipService defaultNamespaceMembershipService;
    private final AuditLogService auditLogService;
    private final RequestIdAccessor requestIdAccessor;
    private final ObjectMapper objectMapper;

    public DefaultNamespaceSettingsAppService(
            DefaultNamespaceMembershipService defaultNamespaceMembershipService,
            AuditLogService auditLogService,
            RequestIdAccessor requestIdAccessor,
            ObjectMapper objectMapper) {
        this.defaultNamespaceMembershipService = defaultNamespaceMembershipService;
        this.auditLogService = auditLogService;
        this.requestIdAccessor = requestIdAccessor;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DefaultNamespaceSettingsResponse get() {
        return new DefaultNamespaceSettingsResponse(
                defaultNamespaceMembershipService.currentSettings().slugs());
    }

    @Transactional
    public DefaultNamespaceSettingsResponse update(DefaultNamespaceSettingsUpdateRequest request,
                                                   String actorUserId,
                                                   AuditRequestContext auditContext) {
        DefaultNamespaceSettings previous = defaultNamespaceMembershipService.currentSettings();
        DefaultNamespaceSettings updated = defaultNamespaceMembershipService.updateSettings(
                new DefaultNamespaceSettings(request.slugs()), actorUserId);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", previous.slugs());
        detail.put("after", updated.slugs());
        record(actorUserId, auditContext, AUDIT_ACTION_UPDATE, detail);
        return new DefaultNamespaceSettingsResponse(updated.slugs());
    }

    /**
     * A dry run writes nothing and is not audited; an applied run records who it enrolled.
     */
    public DefaultNamespaceBackfillResponse backfill(boolean dryRun,
                                                     String actorUserId,
                                                     AuditRequestContext auditContext) {
        DefaultNamespaceBackfillReport report = defaultNamespaceMembershipService.backfill(dryRun);

        if (!dryRun) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("scannedAccounts", report.scannedAccounts());
            detail.put("alreadyEnrolled", report.alreadyEnrolled());
            detail.put("truncated", report.truncated());
            detail.put("enrolled", report.entries().stream()
                    .map(entry -> Map.of("userId", entry.userId(), "slugs", entry.slugs()))
                    .toList());
            record(actorUserId, auditContext, AUDIT_ACTION_BACKFILL, detail);
        }

        return new DefaultNamespaceBackfillResponse(
                report.dryRun(),
                report.scannedAccounts(),
                report.alreadyEnrolled(),
                report.systemAccountsSkipped(),
                report.truncated(),
                report.entries().stream()
                        .map(entry -> new DefaultNamespaceBackfillResponse.Entry(
                                entry.userId(), entry.displayName(), entry.slugs()))
                        .toList());
    }

    private void record(String actorUserId,
                        AuditRequestContext auditContext,
                        String action,
                        Map<String, Object> detail) {
        auditLogService.record(
                actorUserId,
                action,
                AUDIT_TARGET_TYPE,
                null,
                requestIdAccessor.current(),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                toJson(detail));
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return null;
        }
    }
}
