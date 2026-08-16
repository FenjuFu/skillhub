package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param slugs may be empty, which means new accounts are enrolled nowhere
 */
public record DefaultNamespaceSettingsUpdateRequest(
        @NotNull @Size(max = 20) List<@Size(max = 64) String> slugs
) {}
