package com.vanyiung.quantguard.dto;

import java.util.List;

public record BatchDailyPriceSyncRequest(
        List<Long> instrumentIds,
        boolean syncAllImportedInstruments
) {
}
