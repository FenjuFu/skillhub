package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Shared body for the admin backfill endpoints.
 *
 * @param dryRun when true, report what would happen without writing anything
 */
public record BackfillRequest(@NotNull Boolean dryRun) {}
