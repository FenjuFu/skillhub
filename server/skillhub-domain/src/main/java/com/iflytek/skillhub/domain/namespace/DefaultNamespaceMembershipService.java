package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.setting.SystemSettingService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Enrolls newly active users in the namespaces the operator has designated as defaults.
 *
 * <p>This used to be hard-wired to the built-in {@code global} namespace. Deployments that stand up
 * their own organisation-wide namespace found it invisible to everyone, because the namespace
 * listing only returns namespaces the caller belongs to and nothing ever added them.
 */
@Service
public class DefaultNamespaceMembershipService {

    public static final String SETTING_KEY = "namespace.default-membership";

    private static final int MAX_BACKFILL_ACCOUNTS = 5000;
    private static final int BACKFILL_PAGE_SIZE = 200;

    private static final Logger log = LoggerFactory.getLogger(DefaultNamespaceMembershipService.class);

    private final SystemSettingService systemSettingService;
    private final DefaultNamespaceProperties defaults;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final UserAccountRepository userAccountRepository;

    public DefaultNamespaceMembershipService(SystemSettingService systemSettingService,
                                             DefaultNamespaceProperties defaults,
                                             NamespaceRepository namespaceRepository,
                                             NamespaceMemberRepository namespaceMemberRepository,
                                             UserAccountRepository userAccountRepository) {
        this.systemSettingService = systemSettingService;
        this.defaults = defaults;
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.userAccountRepository = userAccountRepository;
    }

    public DefaultNamespaceSettings currentSettings() {
        return systemSettingService.get(SETTING_KEY, DefaultNamespaceSettings.class, defaults.toSettings());
    }

    /**
     * Stores the operator's choice after checking every slug resolves to a live namespace, so a
     * typo surfaces here rather than as a warning on somebody's first login.
     */
    @Transactional
    public DefaultNamespaceSettings updateSettings(DefaultNamespaceSettings settings, String actorUserId) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String slug : settings.slugs()) {
            String trimmed = slug == null ? "" : slug.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Namespace namespace = namespaceRepository.findBySlug(trimmed)
                    .orElseThrow(() -> new DomainBadRequestException(
                            "error.namespace.defaultMembership.unknownSlug", trimmed));
            if (namespace.getStatus() != NamespaceStatus.ACTIVE) {
                throw new DomainBadRequestException(
                        "error.namespace.defaultMembership.inactiveSlug", trimmed);
            }
            normalized.add(trimmed);
        }
        return systemSettingService.put(
                SETTING_KEY, new DefaultNamespaceSettings(List.copyOf(normalized)), actorUserId);
    }

    /**
     * Adds {@code userId} to every configured default namespace it is not already in.
     *
     * <p>A slug that no longer resolves is logged and skipped: a namespace that was renamed or
     * deleted must not cost somebody their registration.
     */
    @Transactional
    public void ensureMember(String userId) {
        for (String slug : currentSettings().slugs()) {
            Optional<Namespace> namespace = namespaceRepository.findBySlug(slug);
            if (namespace.isEmpty()) {
                log.warn("Default namespace '{}' does not exist; skipping enrolment for user {}", slug, userId);
                continue;
            }
            join(namespace.get(), userId);
        }
    }

    /**
     * Enrolls existing accounts in the configured defaults, for when an operator adds a namespace
     * after people have already signed up.
     *
     * <p>Not {@code @Transactional}: each account is enrolled on its own, so one failure does not
     * discard the rest of the run.
     */
    public DefaultNamespaceBackfillReport backfill(boolean dryRun) {
        List<Namespace> targets = new ArrayList<>();
        for (String slug : currentSettings().slugs()) {
            namespaceRepository.findBySlug(slug).ifPresentOrElse(
                    targets::add,
                    () -> log.warn("Default namespace '{}' does not exist; excluded from backfill", slug));
        }

        List<DefaultNamespaceBackfillEntry> entries = new ArrayList<>();
        int scanned = 0;
        int alreadyMember = 0;
        int systemAccounts = 0;
        boolean truncated = false;

        for (int page = 0; !truncated && !targets.isEmpty(); page++) {
            Page<UserAccount> batch = userAccountRepository.findByStatus(UserStatus.ACTIVE,
                    PageRequest.of(page, BACKFILL_PAGE_SIZE, Sort.by("id")));
            if (batch.isEmpty()) {
                break;
            }
            for (UserAccount user : batch) {
                if (scanned >= MAX_BACKFILL_ACCOUNTS) {
                    truncated = true;
                    break;
                }
                scanned++;
                if (user.isSystemAccount()) {
                    systemAccounts++;
                    continue;
                }
                List<String> missing = targets.stream()
                        .filter(namespace -> namespaceMemberRepository
                                .findByNamespaceIdAndUserId(namespace.getId(), user.getId()).isEmpty())
                        .map(Namespace::getSlug)
                        .toList();
                if (missing.isEmpty()) {
                    alreadyMember++;
                    continue;
                }
                if (!dryRun) {
                    targets.stream()
                            .filter(namespace -> missing.contains(namespace.getSlug()))
                            .forEach(namespace -> join(namespace, user.getId()));
                }
                entries.add(new DefaultNamespaceBackfillEntry(user.getId(), user.getDisplayName(), missing));
            }
            if (!batch.hasNext()) {
                break;
            }
        }

        log.info("Default namespace backfill ({}): scanned {}, already enrolled {}, acted on {}{}",
                dryRun ? "dry run" : "applied", scanned, alreadyMember, entries.size(),
                truncated ? ", stopped at the per-run cap" : "");
        return new DefaultNamespaceBackfillReport(
                dryRun, scanned, alreadyMember, systemAccounts, truncated, List.copyOf(entries));
    }

    /**
     * Idempotent. Called from {@link #ensureMember} inside the caller's transaction, and from the
     * backfill outside one, where each save commits on its own.
     */
    private void join(Namespace namespace, String userId) {
        namespaceMemberRepository.findByNamespaceIdAndUserId(namespace.getId(), userId)
                .orElseGet(() -> namespaceMemberRepository.save(
                        new NamespaceMember(namespace.getId(), userId, NamespaceRole.MEMBER)));
    }
}
