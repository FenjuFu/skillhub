package com.iflytek.skillhub.domain.namespace;

import java.util.List;

/**
 * One account a default-namespace backfill enrolled, or would enrol.
 *
 * @param slugs the default namespaces the account is not yet a member of
 */
public record DefaultNamespaceBackfillEntry(String userId, String displayName, List<String> slugs) {

    public DefaultNamespaceBackfillEntry {
        slugs = slugs == null ? List.of() : List.copyOf(slugs);
    }
}
