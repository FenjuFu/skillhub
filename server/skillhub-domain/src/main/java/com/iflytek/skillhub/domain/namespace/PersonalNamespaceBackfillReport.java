package com.iflytek.skillhub.domain.namespace;

import java.util.List;

/**
 * Outcome of a backfill run over existing accounts.
 *
 * <p>{@code entries} lists only the accounts a run would change or could not place, so an operator
 * reads the work rather than the whole directory; accounts that already have a namespace are
 * counted instead.
 *
 * @param truncated whether the run stopped at its per-run account cap, leaving accounts unvisited
 */
public record PersonalNamespaceBackfillReport(
        boolean dryRun,
        int scannedAccounts,
        int alreadyProvisioned,
        int systemAccountsSkipped,
        boolean truncated,
        List<PersonalNamespaceBackfillEntry> entries) {
}
