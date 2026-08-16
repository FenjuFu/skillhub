package com.iflytek.skillhub.domain.namespace;

import java.util.List;

/**
 * Outcome of enrolling existing accounts in the configured default namespaces.
 *
 * @param truncated the run stopped at its per-run account cap; re-run to continue
 * @param entries   only the accounts that were enrolled, or would be
 */
public record DefaultNamespaceBackfillReport(
        boolean dryRun,
        int scannedAccounts,
        int alreadyEnrolled,
        int systemAccountsSkipped,
        boolean truncated,
        List<DefaultNamespaceBackfillEntry> entries) {
}
