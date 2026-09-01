package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.dto.ReviewProgressPageResponse;

/**
 * Query seam for author-facing review progress grouped by skill version.
 */
public interface ReviewProgressQueryRepository {

    ReviewProgressPageResponse findMyProgress(
            String userId,
            ReviewTaskStatus status,
            String query,
            int page,
            int size
    );
}
