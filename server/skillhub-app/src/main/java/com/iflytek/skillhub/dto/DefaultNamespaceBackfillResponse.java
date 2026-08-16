package com.iflytek.skillhub.dto;

import java.util.List;

/**
 * @param truncated the run stopped at its per-run account cap; re-run to continue
 * @param entries   only the accounts that were enrolled, or would be
 */
public record DefaultNamespaceBackfillResponse(
        boolean dryRun,
        int scannedAccounts,
        int alreadyEnrolled,
        int systemAccountsSkipped,
        boolean truncated,
        List<Entry> entries) {

    public record Entry(String userId, String displayName, List<String> slugs) {}
}
