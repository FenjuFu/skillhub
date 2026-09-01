package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ReviewProgressResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL read-model query for review progress.
 *
 * <p>Direct SQL is intentional here: the page boundary applies to grouped skill-version attempts,
 * not individual review tasks. Window functions keep grouping, latest-attempt selection, counts,
 * filtering, and pagination in the database instead of loading an author's full history.</p>
 */
@Repository
public class JpaReviewProgressQueryRepository implements ReviewProgressQueryRepository {

    private static final String MY_PROGRESS_SQL = """
            WITH ranked AS (
                SELECT task.id,
                       task.skill_id,
                       task.namespace_id,
                       task.skill_version,
                       task.status,
                       task.review_comment,
                       task.submitted_at,
                       task.reviewed_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY task.skill_id, task.skill_version
                           ORDER BY task.submitted_at DESC, task.id DESC
                       ) AS attempt_rank,
                       COUNT(*) OVER (
                           PARTITION BY task.skill_id, task.skill_version
                       ) AS attempt_count
                FROM review_task task
                WHERE task.submitted_by = :userId
            ), latest AS (
                SELECT *
                FROM ranked
                WHERE attempt_rank = 1
                  AND (:status = '' OR status = :status)
            )
            SELECT latest.id,
                   latest.skill_id,
                   namespace.slug,
                   skill.slug,
                   latest.skill_version,
                   latest.status,
                   latest.review_comment,
                   latest.submitted_at,
                   latest.reviewed_at,
                   latest.attempt_count,
                   COUNT(*) OVER () AS total_groups
            FROM latest
            JOIN skill ON skill.id = latest.skill_id
            JOIN namespace ON namespace.id = latest.namespace_id
            WHERE :query = ''
               OR LOWER(skill.slug) LIKE :queryPattern
               OR LOWER(namespace.slug) LIKE :queryPattern
            ORDER BY latest.submitted_at DESC, latest.id DESC
            OFFSET :offset ROWS FETCH NEXT :size ROWS ONLY
            """;

    private final EntityManager entityManager;

    public JpaReviewProgressQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewProgressResponse> findMyProgress(
            String userId,
            ReviewTaskStatus status,
            String query,
            int page,
            int size) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        Query nativeQuery = entityManager.createNativeQuery(MY_PROGRESS_SQL)
                .setParameter("userId", userId)
                .setParameter("status", status != null ? status.name() : "")
                .setParameter("query", normalizedQuery)
                .setParameter("queryPattern", "%" + normalizedQuery + "%")
                .setParameter("offset", page * size)
                .setParameter("size", size);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = nativeQuery.getResultList();
        List<ReviewProgressResponse> items = rows.stream().map(this::mapRow).toList();
        long total = rows.isEmpty() ? 0 : number(rows.get(0)[10]).longValue();
        return new PageResponse<>(items, total, page, size);
    }

    private ReviewProgressResponse mapRow(Object[] row) {
        return new ReviewProgressResponse(
                number(row[0]).longValue(),
                number(row[1]).longValue(),
                (String) row[2],
                (String) row[3],
                (String) row[4],
                String.valueOf(row[5]),
                (String) row[6],
                instant(row[7]),
                instant(row[8]),
                number(row[9]).longValue()
        );
    }

    private Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        throw new IllegalStateException("Expected numeric review progress value, got " + value);
    }

    private Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Expected review progress timestamp, got " + value.getClass().getName());
    }
}
