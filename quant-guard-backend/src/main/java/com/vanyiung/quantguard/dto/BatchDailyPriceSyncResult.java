package com.vanyiung.quantguard.dto;

import java.util.List;

public record BatchDailyPriceSyncResult(
        int successCount,
        int failedCount,
        int skippedCount,
        List<DailyPriceSyncResult> items
) {
}
