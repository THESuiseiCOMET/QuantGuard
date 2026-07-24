package com.vanyiung.quantguard.dto;

import java.time.LocalDate;

public record DailyPriceSyncRequest(
        LocalDate startDate,
        LocalDate endDate,
        boolean fullRefresh
) {
}
