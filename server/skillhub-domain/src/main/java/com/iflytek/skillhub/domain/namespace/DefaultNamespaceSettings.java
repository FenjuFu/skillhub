package com.iflytek.skillhub.domain.namespace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The namespaces every newly activated account is enrolled in, as member.
 *
 * <p>A deployment that outgrows the built-in {@code global} namespace — say it wants an
 * organisation-wide space of its own — needs to say so somewhere, because a namespace nobody is a
 * member of is invisible: the namespace listing only returns namespaces the caller belongs to.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DefaultNamespaceSettings(List<String> slugs) {

    public DefaultNamespaceSettings {
        slugs = slugs == null ? List.of() : List.copyOf(slugs);
    }
}
