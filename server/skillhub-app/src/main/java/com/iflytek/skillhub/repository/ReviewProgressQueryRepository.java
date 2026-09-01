package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.review.ReviewTaskStatus;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.dto.ReviewProgressResponse;

/**
 * Query seam for author-facing review progress grouped by skill version.
 */
public interface ReviewProgressQueryRepository {

    PageResponse<ReviewProgressResponse> findMyProgress(
            String userId,
            ReviewTaskStatus status,
            String query,
            int page,
            int size
    );
}
