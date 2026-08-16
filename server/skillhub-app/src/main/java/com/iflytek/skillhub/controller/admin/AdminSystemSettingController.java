package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.BackfillRequest;
import com.iflytek.skillhub.dto.DefaultNamespaceBackfillResponse;
import com.iflytek.skillhub.dto.DefaultNamespaceSettingsResponse;
import com.iflytek.skillhub.dto.DefaultNamespaceSettingsUpdateRequest;
import com.iflytek.skillhub.dto.PersonalNamespaceBackfillResponse;
import com.iflytek.skillhub.dto.PersonalNamespaceSettingsResponse;
import com.iflytek.skillhub.dto.PersonalNamespaceSettingsUpdateRequest;
import com.iflytek.skillhub.service.AuditRequestContext;
import com.iflytek.skillhub.service.DefaultNamespaceSettingsAppService;
import com.iflytek.skillhub.service.PersonalNamespaceSettingsAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-wide settings an operator can change without redeploying.
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
public class AdminSystemSettingController extends BaseApiController {

    private final PersonalNamespaceSettingsAppService personalNamespaceSettingsAppService;
    private final DefaultNamespaceSettingsAppService defaultNamespaceSettingsAppService;

    public AdminSystemSettingController(PersonalNamespaceSettingsAppService personalNamespaceSettingsAppService,
                                        DefaultNamespaceSettingsAppService defaultNamespaceSettingsAppService,
                                        ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.personalNamespaceSettingsAppService = personalNamespaceSettingsAppService;
        this.defaultNamespaceSettingsAppService = defaultNamespaceSettingsAppService;
    }

    @GetMapping("/personal-namespace")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<PersonalNamespaceSettingsResponse> getPersonalNamespaceSettings() {
        return ok("response.success.read", personalNamespaceSettingsAppService.get());
    }

    @PutMapping("/personal-namespace")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<PersonalNamespaceSettingsResponse> updatePersonalNamespaceSettings(
            @Valid @RequestBody PersonalNamespaceSettingsUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", personalNamespaceSettingsAppService.update(
                request, principal.userId(), AuditRequestContext.from(httpRequest)));
    }

    /**
     * Gives existing accounts the namespace they would have received had provisioning been on when
     * they first signed in. Send {@code dryRun} to see the plan first.
     */
    @PostMapping("/personal-namespace/backfill")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<PersonalNamespaceBackfillResponse> backfillPersonalNamespaces(
            @Valid @RequestBody BackfillRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success", personalNamespaceSettingsAppService.backfill(
                request, principal.userId(), AuditRequestContext.from(httpRequest)));
    }

    @GetMapping("/default-namespaces")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<DefaultNamespaceSettingsResponse> getDefaultNamespaces() {
        return ok("response.success.read", defaultNamespaceSettingsAppService.get());
    }

    @PutMapping("/default-namespaces")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<DefaultNamespaceSettingsResponse> updateDefaultNamespaces(
            @Valid @RequestBody DefaultNamespaceSettingsUpdateRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated", defaultNamespaceSettingsAppService.update(
                request, principal.userId(), AuditRequestContext.from(httpRequest)));
    }

    /**
     * Enrols existing accounts in the configured default namespaces, for when one is added after
     * people have already signed up. Send {@code dryRun} to see the plan first.
     */
    @PostMapping("/default-namespaces/backfill")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<DefaultNamespaceBackfillResponse> backfillDefaultNamespaces(
            @Valid @RequestBody BackfillRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {
        return ok("response.success", defaultNamespaceSettingsAppService.backfill(
                Boolean.TRUE.equals(request.dryRun()), principal.userId(),
                AuditRequestContext.from(httpRequest)));
    }
}
