package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.setting.SystemSettingService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Gives each newly activated account a namespace of its own, when the operator has asked for it.
 *
 * <p>The namespace is an ordinary team namespace whose only member is its owner, which is what
 * "private" means in this model: there is no namespace-level visibility flag, and skill visibility
 * stays a property of each skill.
 *
 * <p>Provisioning deliberately runs in its own transaction, after the account has been committed.
 * {@code namespace.created_by} and {@code namespace_member.user_id} both reference
 * {@code user_account(id)}, so creating the namespace inside the still-open registration
 * transaction would either join that transaction — letting a naming clash roll back the
 * registration — or, if suspended, block on the uncommitted account row. Running afterwards keeps a
 * failure here from costing the user their account; see
 * {@code PersonalNamespaceProvisioningListener}.
 */
@Service
public class PersonalNamespaceProvisioningService {

    public static final String SETTING_KEY = "namespace.personal-provisioning";

    /**
     * Upper bound on de-duplication suffixes before giving up on a slug base.
     */
    private static final int MAX_SLUG_ATTEMPTS = 64;

    /**
     * Per-run account cap, so an unexpectedly large directory cannot turn one click into an
     * unbounded job. A run that hits it reports {@code truncated} rather than pretending it
     * covered everything.
     */
    private static final int MAX_BACKFILL_ACCOUNTS = 5000;

    private static final int BACKFILL_PAGE_SIZE = 200;

    private static final Logger log = LoggerFactory.getLogger(PersonalNamespaceProvisioningService.class);

    private final SystemSettingService systemSettingService;
    private final PersonalNamespaceProvisioningProperties defaults;
    private final NamespaceService namespaceService;
    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final UserAccountRepository userAccountRepository;

    public PersonalNamespaceProvisioningService(SystemSettingService systemSettingService,
                                                PersonalNamespaceProvisioningProperties defaults,
                                                NamespaceService namespaceService,
                                                NamespaceRepository namespaceRepository,
                                                NamespaceMemberRepository namespaceMemberRepository,
                                                UserAccountRepository userAccountRepository) {
        this.systemSettingService = systemSettingService;
        this.defaults = defaults;
        this.namespaceService = namespaceService;
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * Returns the effective policy: the administrator's stored choice, or the deployment defaults.
     */
    public PersonalNamespaceSettings currentSettings() {
        return systemSettingService.get(SETTING_KEY, PersonalNamespaceSettings.class, defaults.toSettings());
    }

    @Transactional
    public PersonalNamespaceSettings updateSettings(PersonalNamespaceSettings settings, String actorUserId) {
        return systemSettingService.put(SETTING_KEY, settings, actorUserId);
    }

    /**
     * Creates the owner's namespace, or returns empty when provisioning is off, the owner already
     * has one, or no acceptable slug is available.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Namespace> provisionFor(PersonalNamespaceOwner owner) {
        PersonalNamespaceSettings settings = currentSettings();
        if (!settings.enabled()) {
            log.info("Skipping personal namespace for user {}: provisioning is disabled", owner.userId());
            return Optional.empty();
        }
        if (alreadyOwnsNamespace(owner.userId())) {
            log.info("Skipping personal namespace for user {}: already owns a non-global namespace",
                    owner.userId());
            return Optional.empty();
        }

        String slug = allocateSlug(settings.slugTemplate(), owner, Set.of());
        if (slug == null) {
            log.warn("No namespace slug available for user {} from template '{}'; skipping provisioning",
                    owner.userId(), settings.slugTemplate());
            return Optional.empty();
        }

        String displayName = PersonalNamespaceNaming.displayName(settings.displayNameTemplate(), owner, slug);
        Namespace namespace = namespaceService.createNamespace(slug, displayName, null, owner.userId());
        log.info("Provisioned personal namespace '{}' for user {}", slug, owner.userId());
        return Optional.of(namespace);
    }

    /**
     * Gives existing accounts the namespace they would have received had provisioning been on when
     * they first signed in.
     *
     * <p>Turning the setting on only affects accounts activated afterwards, which on a registry
     * that has been running for a while means nobody. This walks the active accounts and fills the
     * gap.
     *
     * <p>Deliberately not {@code @Transactional}: each namespace is created in its own transaction,
     * so one account that cannot be placed does not discard the rest of the run.
     *
     * @param dryRun report what would happen without writing anything
     */
    public PersonalNamespaceBackfillReport backfill(boolean dryRun) {
        PersonalNamespaceSettings settings = currentSettings();
        List<PersonalNamespaceBackfillEntry> entries = new ArrayList<>();
        Set<String> reserved = new HashSet<>();
        int scanned = 0;
        int alreadyProvisioned = 0;
        int systemAccounts = 0;
        boolean truncated = false;

        for (int page = 0; !truncated; page++) {
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
                if (alreadyOwnsNamespace(user.getId())) {
                    alreadyProvisioned++;
                    continue;
                }
                entries.add(placeAccount(user, settings, reserved, dryRun));
            }
            if (!batch.hasNext()) {
                break;
            }
        }

        log.info("Personal namespace backfill ({}): scanned {}, already provisioned {}, acted on {}{}",
                dryRun ? "dry run" : "applied", scanned, alreadyProvisioned, entries.size(),
                truncated ? ", stopped at the per-run cap" : "");
        return new PersonalNamespaceBackfillReport(
                dryRun, scanned, alreadyProvisioned, systemAccounts, truncated, List.copyOf(entries));
    }

    private PersonalNamespaceBackfillEntry placeAccount(UserAccount user,
                                                        PersonalNamespaceSettings settings,
                                                        Set<String> reserved,
                                                        boolean dryRun) {
        PersonalNamespaceOwner owner =
                new PersonalNamespaceOwner(user.getId(), user.getDisplayName(), user.getEmail());
        String slug = allocateSlug(settings.slugTemplate(), owner, reserved);
        if (slug == null) {
            log.warn("Backfill found no available slug for user {} from template '{}'",
                    user.getId(), settings.slugTemplate());
            return new PersonalNamespaceBackfillEntry(user.getId(), user.getDisplayName(), null,
                    PersonalNamespaceBackfillEntry.Outcome.NO_SLUG);
        }
        reserved.add(slug);
        if (dryRun) {
            return new PersonalNamespaceBackfillEntry(user.getId(), user.getDisplayName(), slug,
                    PersonalNamespaceBackfillEntry.Outcome.PLANNED);
        }

        String displayName = PersonalNamespaceNaming.displayName(settings.displayNameTemplate(), owner, slug);
        namespaceService.createNamespace(slug, displayName, null, user.getId());
        log.info("Backfilled personal namespace '{}' for user {}", slug, user.getId());
        return new PersonalNamespaceBackfillEntry(user.getId(), user.getDisplayName(), slug,
                PersonalNamespaceBackfillEntry.Outcome.CREATED);
    }

    /**
     * Treats owning any non-global namespace as "already has a personal namespace", which keeps a
     * repeated activation from handing the same user a second one.
     */
    private boolean alreadyOwnsNamespace(String userId) {
        return namespaceMemberRepository.findByUserId(userId).stream()
                .filter(member -> member.getRole() == NamespaceRole.OWNER)
                .map(member -> namespaceRepository.findById(member.getNamespaceId()))
                .flatMap(Optional::stream)
                .anyMatch(namespace -> namespace.getType() != NamespaceType.GLOBAL);
    }

    /**
     * Returns the first free slug for the owner, or {@code null} when every candidate is taken or
     * rejected — for example when the template renders to a reserved word for many users.
     *
     * @param reserved slugs already promised to earlier owners in this run but not yet persisted,
     *                 so a batch cannot hand the same slug to two accounts
     */
    private String allocateSlug(String slugTemplate, PersonalNamespaceOwner owner, Set<String> reserved) {
        String base = PersonalNamespaceNaming.slugBase(slugTemplate, owner);
        for (int attempt = 1; attempt <= MAX_SLUG_ATTEMPTS; attempt++) {
            String candidate = attempt == 1 ? base : base + "-" + attempt;
            if (!reserved.contains(candidate)
                    && SlugValidator.isValid(candidate)
                    && namespaceRepository.findBySlug(candidate).isEmpty()) {
                return candidate;
            }
        }
        return null;
    }
}
