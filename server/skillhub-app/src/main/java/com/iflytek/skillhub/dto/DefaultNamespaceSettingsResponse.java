package com.iflytek.skillhub.dto;

import java.util.List;

/**
 * @param slugs namespaces every newly activated account is enrolled in
 */
public record DefaultNamespaceSettingsResponse(List<String> slugs) {}
