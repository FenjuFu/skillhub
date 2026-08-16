package com.iflytek.skillhub.domain.namespace;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deployment default for {@link DefaultNamespaceSettings}, used until an administrator saves a
 * choice in the admin console.
 *
 * <p>Defaults to the built-in global namespace, which is what every deployment did before this was
 * configurable.
 */
@Component
@ConfigurationProperties(prefix = "skillhub.namespace.default-membership")
public class DefaultNamespaceProperties {

    private List<String> slugs = new ArrayList<>(List.of("global"));

    public List<String> getSlugs() {
        return slugs;
    }

    public void setSlugs(List<String> slugs) {
        this.slugs = slugs;
    }

    public DefaultNamespaceSettings toSettings() {
        return new DefaultNamespaceSettings(slugs);
    }
}
