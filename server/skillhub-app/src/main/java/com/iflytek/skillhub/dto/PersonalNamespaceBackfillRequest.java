package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;

/**
 * @param dryRun when true, report the accounts that would get a namespace without creating any
 */
public record PersonalNamespaceBackfillRequest(@NotNull Boolean dryRun) {}
