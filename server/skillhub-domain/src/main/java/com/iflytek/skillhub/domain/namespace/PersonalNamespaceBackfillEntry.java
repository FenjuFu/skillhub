package com.iflytek.skillhub.domain.namespace;

/**
 * One account a backfill run acted on, or wanted to act on.
 *
 * @param slug the slug that was taken, or would be; {@code null} when none was available
 */
public record PersonalNamespaceBackfillEntry(
        String userId,
        String displayName,
        String slug,
        Outcome outcome) {

    public enum Outcome {
        /** Dry run: this account would get {@code slug}. */
        PLANNED,
        /** The namespace was created. */
        CREATED,
        /** Every candidate slug was taken or rejected, so the account was left alone. */
        NO_SLUG
    }
}
