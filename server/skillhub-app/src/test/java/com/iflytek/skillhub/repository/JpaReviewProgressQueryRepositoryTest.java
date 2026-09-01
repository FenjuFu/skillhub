package com.iflytek.skillhub.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.review.ReviewTask;
import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillVisibility;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaReviewProgressQueryRepository.class)
class JpaReviewProgressQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JpaReviewProgressQueryRepository repository;

    @Test
    void groupsAttemptsFiltersLatestStatusAndKeepsTotalsOnEmptyPage() {
        Namespace namespace = entityManager.persistFlushFind(
                new Namespace("team-review", "Review Team", "owner"));
        Skill alpha = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "alpha-skill", "author-1", SkillVisibility.PUBLIC));
        Skill beta = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "beta-skill", "author-1", SkillVisibility.PUBLIC));
        Skill gamma = entityManager.persistFlushFind(
                new Skill(namespace.getId(), "gamma-skill", "author-1", SkillVisibility.PUBLIC));

        persistAttempt(
                alpha,
                namespace,
                "author-1",
                "1.0.0",
                ReviewTaskStatus.REJECTED,
                Instant.parse("2026-08-30T10:00:00Z"));
        persistAttempt(
                alpha,
                namespace,
                "author-1",
                "1.0.0",
                ReviewTaskStatus.PENDING,
                Instant.parse("2026-08-31T10:00:00Z"));
        persistAttempt(
                beta,
                namespace,
                "author-1",
                "2.0.0",
                ReviewTaskStatus.APPROVED,
                Instant.parse("2026-08-29T10:00:00Z"));
        persistAttempt(
                gamma,
                namespace,
                "author-1",
                "3.0.0",
                ReviewTaskStatus.REJECTED,
                Instant.parse("2026-08-28T10:00:00Z"));
        persistAttempt(
                beta,
                namespace,
                "other-author",
                "3.0.0",
                ReviewTaskStatus.REJECTED,
                Instant.parse("2026-08-31T11:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        var firstPage = repository.findMyProgress("author-1", null, "", 0, 1);

        assertThat(firstPage.total()).isEqualTo(3);
        assertThat(firstPage.items()).singleElement().satisfies(item -> {
            assertThat(item.skillSlug()).isEqualTo("alpha-skill");
            assertThat(item.latestStatus()).isEqualTo("PENDING");
            assertThat(item.attemptCount()).isEqualTo(2);
        });
        assertThat(firstPage.statusCounts().pending()).isEqualTo(1);
        assertThat(firstPage.statusCounts().approved()).isEqualTo(1);
        assertThat(firstPage.statusCounts().rejected()).isEqualTo(1);

        var emptyPage = repository.findMyProgress("author-1", null, "", 8, 1);
        assertThat(emptyPage.items()).isEmpty();
        assertThat(emptyPage.total()).isEqualTo(3);

        var maximumPage = repository.findMyProgress(
                "author-1", null, "", Integer.MAX_VALUE, 100);
        assertThat(maximumPage.items()).isEmpty();
        assertThat(maximumPage.total()).isEqualTo(3);

        var searchedAndFiltered = repository.findMyProgress(
                "author-1", ReviewTaskStatus.APPROVED, "BETA", 0, 20);
        assertThat(searchedAndFiltered.items()).singleElement()
                .satisfies(item -> assertThat(item.skillSlug()).isEqualTo("beta-skill"));
        assertThat(searchedAndFiltered.total()).isEqualTo(1);
        assertThat(searchedAndFiltered.statusCounts().approved()).isEqualTo(1);

        var searchMiss = repository.findMyProgress("author-1", null, "missing", 0, 20);
        assertThat(searchMiss.items()).isEmpty();
        assertThat(searchMiss.total()).isZero();
        assertThat(searchMiss.statusCounts().pending()).isZero();
        assertThat(searchMiss.statusCounts().approved()).isZero();
        assertThat(searchMiss.statusCounts().rejected()).isZero();
    }

    private void persistAttempt(
            Skill skill,
            Namespace namespace,
            String author,
            String version,
            ReviewTaskStatus status,
            Instant submittedAt) {
        ReviewTask task = new ReviewTask(
                null, skill.getId(), namespace.getId(), version, author);
        task.setStatus(status);
        setField(task, "submittedAt", submittedAt);
        entityManager.persist(task);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
