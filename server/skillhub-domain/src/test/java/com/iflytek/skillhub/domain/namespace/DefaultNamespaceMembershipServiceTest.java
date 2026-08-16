package com.iflytek.skillhub.domain.namespace;

import com.iflytek.skillhub.domain.setting.SystemSettingService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
import com.iflytek.skillhub.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultNamespaceMembershipServiceTest {

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private NamespaceRepository namespaceRepository;

    @Mock
    private NamespaceMemberRepository namespaceMemberRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    private DefaultNamespaceMembershipService service;

    @BeforeEach
    void setUp() {
        service = new DefaultNamespaceMembershipService(
                systemSettingService,
                new DefaultNamespaceProperties(),
                namespaceRepository,
                namespaceMemberRepository,
                userAccountRepository);
    }

    private Namespace namespace(long id, String slug) {
        Namespace namespace = new Namespace(slug, slug, "usr_owner");
        try {
            Field field = Namespace.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(namespace, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return namespace;
    }

    private void configured(String... slugs) {
        when(systemSettingService.get(eq(DefaultNamespaceMembershipService.SETTING_KEY),
                eq(DefaultNamespaceSettings.class), any()))
                .thenReturn(new DefaultNamespaceSettings(List.of(slugs)));
    }

    @Test
    void defaultsToTheBuiltInGlobalNamespace() {
        assertEquals(List.of("global"), new DefaultNamespaceProperties().toSettings().slugs());
    }

    @Test
    void ensureMemberJoinsEveryConfiguredNamespace() {
        configured("global", "musee");
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace(1L, "global")));
        when(namespaceRepository.findBySlug("musee")).thenReturn(Optional.of(namespace(2L, "musee")));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(any(), eq("usr_1")))
                .thenReturn(Optional.empty());

        service.ensureMember("usr_1");

        ArgumentCaptor<NamespaceMember> captor = ArgumentCaptor.forClass(NamespaceMember.class);
        verify(namespaceMemberRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals(List.of(1L, 2L), captor.getAllValues().stream().map(NamespaceMember::getNamespaceId).toList());
        assertTrue(captor.getAllValues().stream().allMatch(m -> m.getRole() == NamespaceRole.MEMBER));
    }

    @Test
    void ensureMemberSkipsASlugThatNoLongerResolves() {
        configured("global", "deleted-one");
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace(1L, "global")));
        when(namespaceRepository.findBySlug("deleted-one")).thenReturn(Optional.empty());
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "usr_1")).thenReturn(Optional.empty());

        service.ensureMember("usr_1");

        verify(namespaceMemberRepository, org.mockito.Mockito.times(1)).save(any(NamespaceMember.class));
    }

    @Test
    void ensureMemberIsIdempotent() {
        configured("global");
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace(1L, "global")));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "usr_1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "usr_1", NamespaceRole.MEMBER)));

        service.ensureMember("usr_1");

        verify(namespaceMemberRepository, never()).save(any(NamespaceMember.class));
    }

    @Test
    void updateSettingsRejectsASlugThatDoesNotExist() {
        when(namespaceRepository.findBySlug("typo")).thenReturn(Optional.empty());

        assertThrows(DomainBadRequestException.class, () ->
                service.updateSettings(new DefaultNamespaceSettings(List.of("typo")), "usr_admin"));
        verify(systemSettingService, never()).put(any(), any(), any());
    }

    @Test
    void updateSettingsRejectsANamespaceThatIsNotActive() {
        Namespace archived = namespace(3L, "old");
        archived.setStatus(NamespaceStatus.ARCHIVED);
        when(namespaceRepository.findBySlug("old")).thenReturn(Optional.of(archived));

        assertThrows(DomainBadRequestException.class, () ->
                service.updateSettings(new DefaultNamespaceSettings(List.of("old")), "usr_admin"));
    }

    @Test
    void updateSettingsTrimsBlanksAndDropsDuplicates() {
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(namespace(1L, "global")));
        when(systemSettingService.put(any(), any(), any())).thenAnswer(i -> i.getArgument(1));

        service.updateSettings(new DefaultNamespaceSettings(List.of(" global ", "", "global")), "usr_admin");

        ArgumentCaptor<DefaultNamespaceSettings> captor =
                ArgumentCaptor.forClass(DefaultNamespaceSettings.class);
        verify(systemSettingService).put(eq(DefaultNamespaceMembershipService.SETTING_KEY),
                captor.capture(), eq("usr_admin"));
        assertEquals(List.of("global"), captor.getValue().slugs());
    }

    @Test
    void backfillDryRunListsAccountsMissingMembershipWithoutWriting() {
        configured("musee");
        when(namespaceRepository.findBySlug("musee")).thenReturn(Optional.of(namespace(2L, "musee")));
        when(userAccountRepository.findByStatus(eq(UserStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(new UserAccount("usr_1", "alice", null, null))));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(2L, "usr_1")).thenReturn(Optional.empty());

        DefaultNamespaceBackfillReport report = service.backfill(true);

        assertEquals(1, report.entries().size());
        assertEquals(List.of("musee"), report.entries().getFirst().slugs());
        verify(namespaceMemberRepository, never()).save(any(NamespaceMember.class));
    }

    @Test
    void backfillEnrollsAccountsThatAreMissing() {
        configured("musee");
        when(namespaceRepository.findBySlug("musee")).thenReturn(Optional.of(namespace(2L, "musee")));
        when(userAccountRepository.findByStatus(eq(UserStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(new UserAccount("usr_1", "alice", null, null))));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(2L, "usr_1")).thenReturn(Optional.empty());

        service.backfill(false);

        verify(namespaceMemberRepository).save(any(NamespaceMember.class));
    }

    @Test
    void backfillCountsAccountsThatAreAlreadyEnrolled() {
        configured("musee");
        when(namespaceRepository.findBySlug("musee")).thenReturn(Optional.of(namespace(2L, "musee")));
        when(userAccountRepository.findByStatus(eq(UserStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(new UserAccount("usr_1", "alice", null, null))));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(2L, "usr_1"))
                .thenReturn(Optional.of(new NamespaceMember(2L, "usr_1", NamespaceRole.MEMBER)));

        DefaultNamespaceBackfillReport report = service.backfill(false);

        assertEquals(1, report.alreadyEnrolled());
        assertTrue(report.entries().isEmpty());
    }

    @Test
    void backfillLeavesSystemAccountsAlone() {
        configured("musee");
        when(namespaceRepository.findBySlug("musee")).thenReturn(Optional.of(namespace(2L, "musee")));
        when(userAccountRepository.findByStatus(eq(UserStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(
                        UserAccount.systemAccount("builtin-skill-publisher", "Built-in", null, null))));

        DefaultNamespaceBackfillReport report = service.backfill(false);

        assertEquals(1, report.systemAccountsSkipped());
        assertTrue(report.entries().isEmpty());
    }
}
