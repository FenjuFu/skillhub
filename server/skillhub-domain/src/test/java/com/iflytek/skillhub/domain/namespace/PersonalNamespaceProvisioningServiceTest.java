package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.setting.SystemSettingService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonalNamespaceProvisioningServiceTest {

    private static final PersonalNamespaceOwner ALICE =
            new PersonalNamespaceOwner("usr_alice", "alice", "alice@example.com");

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private NamespaceService namespaceService;

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private NamespaceMemberRepository namespaceMemberRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    private PersonalNamespaceProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new PersonalNamespaceProvisioningService(
                systemSettingService,
                new PersonalNamespaceProvisioningProperties(),
                namespaceService,
                namespaceRepository,
                namespaceMemberRepository,
                userAccountRepository);
    }

    private UserAccount account(String id, String displayName, String email) {
        return new UserAccount(id, displayName, email, null);
    }

    private void directoryContains(UserAccount... users) {
        when(userAccountRepository.search(isNull(), eq(UserStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(users)));
    }

    private void withSettings(boolean enabled, String slugTemplate, String displayNameTemplate) {
        when(systemSettingService.get(eq(PersonalNamespaceProvisioningService.SETTING_KEY),
                eq(PersonalNamespaceSettings.class), any()))
                .thenReturn(new PersonalNamespaceSettings(enabled, slugTemplate, displayNameTemplate));
    }

    private void ownsNothing() {
        when(namespaceMemberRepository.findByUserId(ALICE.userId())).thenReturn(List.of());
    }

    /**
     * Mirrors {@link NamespaceService#createNamespace} returning the namespace it persisted.
     */
    private void namespaceCreationSucceeds() {
        when(namespaceService.createNamespace(any(), any(), any(), any()))
                .thenAnswer(invocation -> new Namespace(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(3)));
    }

    @Test
    void doesNothingWhenProvisioningIsDisabled() {
        withSettings(false, "${username}", "${username}");

        assertTrue(service.provisionFor(ALICE).isEmpty());
        verify(namespaceService, never()).createNamespace(any(), any(), any(), any());
    }

    @Test
    void defaultsAreDisabledSoUpgradesDoNotStartCreatingNamespaces() {
        assertEquals(false, new PersonalNamespaceProvisioningProperties().isEnabled());
    }

    @Test
    void createsNamespaceFromTheConfiguredTemplate() {
        withSettings(true, "${username}-space", "${username}'s space");
        ownsNothing();
        namespaceCreationSucceeds();
        when(namespaceRepository.findBySlug("alice-space")).thenReturn(Optional.empty());

        service.provisionFor(ALICE);

        verify(namespaceService).createNamespace("alice-space", "alice's space", null, "usr_alice");
    }

    @Test
    void appendsSuffixWhenTheSlugIsAlreadyTaken() {
        withSettings(true, "${username}", "${username}");
        ownsNothing();
        namespaceCreationSucceeds();
        when(namespaceRepository.findBySlug("alice")).thenReturn(Optional.of(new Namespace("alice", "Alice", "usr_x")));
        when(namespaceRepository.findBySlug("alice-2")).thenReturn(Optional.empty());

        service.provisionFor(ALICE);

        verify(namespaceService).createNamespace(eq("alice-2"), any(), isNull(), eq("usr_alice"));
    }

    @Test
    void skipsReservedSlugsInsteadOfFailing() {
        PersonalNamespaceOwner admin = new PersonalNamespaceOwner("usr_admin", "admin", null);
        withSettings(true, "${username}", "${username}");
        when(namespaceMemberRepository.findByUserId(admin.userId())).thenReturn(List.of());
        namespaceCreationSucceeds();
        when(namespaceRepository.findBySlug("admin-2")).thenReturn(Optional.empty());

        service.provisionFor(admin);

        verify(namespaceService).createNamespace(eq("admin-2"), any(), isNull(), eq("usr_admin"));
    }

    @Test
    void skipsWhenTheUserAlreadyOwnsANamespace() {
        withSettings(true, "${username}", "${username}");
        when(namespaceMemberRepository.findByUserId(ALICE.userId()))
                .thenReturn(List.of(new NamespaceMember(7L, ALICE.userId(), NamespaceRole.OWNER)));
        when(namespaceRepository.findById(7L))
                .thenReturn(Optional.of(new Namespace("alice", "Alice", ALICE.userId())));

        assertTrue(service.provisionFor(ALICE).isEmpty());
        verify(namespaceService, never()).createNamespace(any(), any(), any(), any());
    }

    @Test
    void globalMembershipDoesNotCountAsOwningANamespace() {
        withSettings(true, "${username}", "${username}");
        Namespace global = new Namespace("global", "Global", "usr_system");
        global.setType(NamespaceType.GLOBAL);
        when(namespaceMemberRepository.findByUserId(ALICE.userId()))
                .thenReturn(List.of(new NamespaceMember(1L, ALICE.userId(), NamespaceRole.OWNER)));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(global));
        namespaceCreationSucceeds();
        when(namespaceRepository.findBySlug("alice")).thenReturn(Optional.empty());

        service.provisionFor(ALICE);

        verify(namespaceService).createNamespace(eq("alice"), any(), isNull(), eq("usr_alice"));
    }

    @Test
    void plainMembershipDoesNotCountAsOwningANamespace() {
        withSettings(true, "${username}", "${username}");
        when(namespaceMemberRepository.findByUserId(ALICE.userId()))
                .thenReturn(List.of(new NamespaceMember(3L, ALICE.userId(), NamespaceRole.MEMBER)));
        namespaceCreationSucceeds();
        when(namespaceRepository.findBySlug("alice")).thenReturn(Optional.empty());

        service.provisionFor(ALICE);

        verify(namespaceService).createNamespace(eq("alice"), any(), isNull(), eq("usr_alice"));
    }

    @Test
    void backfillDryRunPlansWithoutCreatingAnything() {
        withSettings(true, "${username}", "${username}");
        directoryContains(account("usr_alice", "alice", "alice@example.com"));
        when(namespaceMemberRepository.findByUserId("usr_alice")).thenReturn(List.of());
        when(namespaceRepository.findBySlug("alice")).thenReturn(Optional.empty());

        PersonalNamespaceBackfillReport report = service.backfill(true);

        assertTrue(report.dryRun());
        assertEquals(1, report.scannedAccounts());
        assertEquals(1, report.entries().size());
        assertEquals("alice", report.entries().getFirst().slug());
        assertEquals(PersonalNamespaceBackfillEntry.Outcome.PLANNED, report.entries().getFirst().outcome());
        verify(namespaceService, never()).createNamespace(any(), any(), any(), any());
    }

    @Test
    void backfillCreatesNamespacesForAccountsThatHaveNone() {
        withSettings(true, "${username}", "${username}");
        directoryContains(account("usr_alice", "alice", "alice@example.com"));
        when(namespaceMemberRepository.findByUserId("usr_alice")).thenReturn(List.of());
        when(namespaceRepository.findBySlug("alice")).thenReturn(Optional.empty());
        namespaceCreationSucceeds();

        PersonalNamespaceBackfillReport report = service.backfill(false);

        assertFalse(report.dryRun());
        assertEquals(PersonalNamespaceBackfillEntry.Outcome.CREATED, report.entries().getFirst().outcome());
        verify(namespaceService).createNamespace(eq("alice"), any(), isNull(), eq("usr_alice"));
    }

    @Test
    void backfillCountsAccountsThatAlreadyHaveANamespaceInsteadOfListingThem() {
        withSettings(true, "${username}", "${username}");
        directoryContains(account("usr_alice", "alice", "alice@example.com"));
        when(namespaceMemberRepository.findByUserId("usr_alice"))
                .thenReturn(List.of(new NamespaceMember(7L, "usr_alice", NamespaceRole.OWNER)));
        when(namespaceRepository.findById(7L))
                .thenReturn(Optional.of(new Namespace("alice", "Alice", "usr_alice")));

        PersonalNamespaceBackfillReport report = service.backfill(false);

        assertEquals(1, report.alreadyProvisioned());
        assertTrue(report.entries().isEmpty());
        verify(namespaceService, never()).createNamespace(any(), any(), any(), any());
    }

    @Test
    void backfillLeavesSystemAccountsAlone() {
        withSettings(true, "${username}", "${username}");
        directoryContains(UserAccount.systemAccount(
                "builtin-skill-publisher", "Built-in Skill Publisher", null, null));

        PersonalNamespaceBackfillReport report = service.backfill(false);

        assertEquals(1, report.systemAccountsSkipped());
        assertTrue(report.entries().isEmpty());
        verify(namespaceService, never()).createNamespace(any(), any(), any(), any());
    }

    @Test
    void backfillDoesNotPromiseTheSameSlugToTwoAccountsInOneRun() {
        withSettings(true, "${username}", "${username}");
        directoryContains(
                account("usr_1", "alice", "alice@example.com"),
                account("usr_2", "Alice", "alice2@example.com"));
        when(namespaceMemberRepository.findByUserId(any())).thenReturn(List.of());
        when(namespaceRepository.findBySlug(any())).thenReturn(Optional.empty());

        PersonalNamespaceBackfillReport report = service.backfill(true);

        assertEquals(List.of("alice", "alice-2"),
                report.entries().stream().map(PersonalNamespaceBackfillEntry::slug).toList());
    }

    @Test
    void backfillReportsAccountsItCannotPlace() {
        withSettings(true, "${username}", "${username}");
        directoryContains(account("usr_alice", "alice", "alice@example.com"));
        when(namespaceMemberRepository.findByUserId("usr_alice")).thenReturn(List.of());
        when(namespaceRepository.findBySlug(any()))
                .thenReturn(Optional.of(new Namespace("taken", "Taken", "usr_x")));

        PersonalNamespaceBackfillReport report = service.backfill(true);

        assertEquals(PersonalNamespaceBackfillEntry.Outcome.NO_SLUG, report.entries().getFirst().outcome());
        assertEquals(null, report.entries().getFirst().slug());
    }

    @Test
    void givesUpQuietlyWhenEveryCandidateSlugIsTaken() {
        withSettings(true, "${username}", "${username}");
        ownsNothing();
        when(namespaceRepository.findBySlug(any()))
                .thenReturn(Optional.of(new Namespace("taken", "Taken", "usr_x")));

        assertTrue(service.provisionFor(ALICE).isEmpty());
        verify(namespaceService, never()).createNamespace(any(), any(), any(), any());
    }
}
