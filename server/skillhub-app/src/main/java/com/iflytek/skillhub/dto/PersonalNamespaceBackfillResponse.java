package com.iflytek.skillhub.dto;

import java.util.List;

/**
 * @param truncated the run stopped at its per-run account cap; re-run to continue
 * @param entries   only the accounts that were changed, or could not be placed
 */
public record PersonalNamespaceBackfillResponse(
        boolean dryRun,
        int scannedAccounts,
        int alreadyProvisioned,
        int systemAccountsSkipped,
        boolean truncated,
        List<Entry> entries) {

    /**
     * @param outcome one of {@code PLANNED}, {@code CREATED}, {@code NO_SLUG}
     */
    public record Entry(String userId, String displayName, String slug, String outcome) {}
}
