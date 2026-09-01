package com.iflytek.skillhub.dto;

import java.time.Instant;

/**
 * Author-facing summary for one skill version's review attempts.
 */
public record ReviewProgressResponse(
        Long latestReviewTaskId,
        Long skillId,
        String namespace,
        String skillSlug,
        String skillVersion,
        String latestStatus,
        String latestReviewComment,
        Instant latestSubmittedAt,
        Instant latestReviewedAt,
        long attemptCount
) {}
