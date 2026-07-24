package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.BatchDailyPriceSyncRequest;
import com.vanyiung.quantguard.dto.BatchDailyPriceSyncResult;
import com.vanyiung.quantguard.dto.DailyPriceResponse;
import com.vanyiung.quantguard.dto.DailyPriceSummaryResponse;
import com.vanyiung.quantguard.dto.DailyPriceSyncRequest;
import com.vanyiung.quantguard.dto.DailyPriceSyncResult;
import com.vanyiung.quantguard.dto.ImportInstrumentsRequest;
import com.vanyiung.quantguard.dto.InstrumentImportPreviewItem;
import com.vanyiung.quantguard.dto.InstrumentImportPreviewRequest;
import com.vanyiung.quantguard.dto.InstrumentImportResult;

import java.time.LocalDate;
import java.util.List;

public interface MarketDataService {

    List<InstrumentImportPreviewItem> previewImport(InstrumentImportPreviewRequest request);

    List<InstrumentImportResult> importInstruments(ImportInstrumentsRequest request);

    DailyPriceSyncResult syncInstrumentDailyPrices(Long instrumentId, DailyPriceSyncRequest request, String syncType);

    BatchDailyPriceSyncResult syncDailyPrices(BatchDailyPriceSyncRequest request, String syncType);

    List<DailyPriceResponse> listDailyPrices(Long instrumentId, LocalDate from, LocalDate to, Integer limit);

    DailyPriceSummaryResponse getDailyPriceSummary(Long instrumentId);
}
