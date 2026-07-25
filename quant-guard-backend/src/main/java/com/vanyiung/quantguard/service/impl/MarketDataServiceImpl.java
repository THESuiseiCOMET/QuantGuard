package com.vanyiung.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import com.vanyiung.quantguard.entity.InstrumentDailyPrice;
import com.vanyiung.quantguard.entity.MarketDataSyncLog;
import com.vanyiung.quantguard.entity.SecurityInstrument;
import com.vanyiung.quantguard.enums.AdjustmentType;
import com.vanyiung.quantguard.enums.AssetType;
import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.enums.InstrumentStatus;
import com.vanyiung.quantguard.enums.MarketDataSyncStatus;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.mapper.InstrumentDailyPriceMapper;
import com.vanyiung.quantguard.mapper.MarketDataSyncLogMapper;
import com.vanyiung.quantguard.mapper.SecurityInstrumentMapper;
import com.vanyiung.quantguard.marketdata.config.MarketDataProperties;
import com.vanyiung.quantguard.marketdata.provider.MarketDataProvider;
import com.vanyiung.quantguard.marketdata.provider.MarketDataProviderException;
import com.vanyiung.quantguard.marketdata.provider.ProviderDailyBar;
import com.vanyiung.quantguard.marketdata.provider.ProviderInstrumentMetadata;
import com.vanyiung.quantguard.marketdata.symbol.MarketSymbol;
import com.vanyiung.quantguard.marketdata.symbol.MarketSymbolParseResult;
import com.vanyiung.quantguard.marketdata.symbol.MarketSymbolParser;
import com.vanyiung.quantguard.service.MarketDataService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MarketDataServiceImpl implements MarketDataService {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_DAILY_PRICE_LIMIT = 10000;
    private static final int DEFAULT_DAILY_PRICE_LIMIT = 400;
    private static final int MAX_RANGE_YEARS = 30;

    private final SecurityInstrumentMapper securityInstrumentMapper;
    private final InstrumentDailyPriceMapper dailyPriceMapper;
    private final MarketDataSyncLogMapper syncLogMapper;
    private final MarketDataProvider marketDataProvider;
    private final MarketDataProperties properties;
    private final MarketSymbolParser symbolParser;

    public MarketDataServiceImpl(SecurityInstrumentMapper securityInstrumentMapper,
                                 InstrumentDailyPriceMapper dailyPriceMapper,
                                 MarketDataSyncLogMapper syncLogMapper,
                                 MarketDataProvider marketDataProvider,
                                 MarketDataProperties properties,
                                 MarketSymbolParser symbolParser) {
        this.securityInstrumentMapper = securityInstrumentMapper;
        this.dailyPriceMapper = dailyPriceMapper;
        this.syncLogMapper = syncLogMapper;
        this.marketDataProvider = marketDataProvider;
        this.properties = properties;
        this.symbolParser = symbolParser;
    }

    @Override
    @Transactional(readOnly = true)
    public List<InstrumentImportPreviewItem> previewImport(InstrumentImportPreviewRequest request) {
        List<MarketSymbolParseResult> parsed = symbolParser.parseInputs(request.codes());
        List<MarketSymbol> validSymbols = parsed.stream()
                .filter(MarketSymbolParseResult::valid)
                .map(MarketSymbolParseResult::symbol)
                .toList();
        Map<String, ProviderInstrumentMetadata> metadataByCode = new LinkedHashMap<>();
        String providerErrorMessage = null;
        try {
            metadataByCode = lookupMetadata(validSymbols);
        } catch (MarketDataProviderException ex) {
            providerErrorMessage = ex.getMessage();
        }

        List<InstrumentImportPreviewItem> items = new ArrayList<>();
        for (MarketSymbolParseResult item : parsed) {
            if (!item.valid()) {
                items.add(new InstrumentImportPreviewItem(
                        item.originalInput(), null, null, null, null, null, AssetType.STOCK,
                        null, null, null, false, null, false, item.message()));
                continue;
            }
            MarketSymbol symbol = item.symbol();
            ProviderInstrumentMetadata metadata = metadataByCode.get(symbol.canonicalCode());
            SecurityInstrument existing = findExisting(symbol);
            ProviderInstrumentMetadata effectiveMetadata = metadataOrFallback(symbol, metadata);
            items.add(new InstrumentImportPreviewItem(
                    item.originalInput(), symbol.canonicalCode(), symbol.symbol(), symbol.exchange(),
                    effectiveMetadata.instrumentName(), "CNY", AssetType.STOCK, lotSizeFor(symbol),
                    effectiveMetadata.listStatus(), effectiveMetadata.listDate(), existing != null,
                    existing == null ? null : existing.getId(), true,
                    metadata == null ? metadataFallbackMessage(providerErrorMessage) : "OK"));
        }
        return items;
    }

    @Override
    public List<InstrumentImportResult> importInstruments(ImportInstrumentsRequest request) {
        List<MarketSymbolParseResult> parsed = symbolParser.parseInputs(request.codes());
        List<InstrumentImportResult> results = new ArrayList<>();
        for (MarketSymbolParseResult item : parsed) {
            if (!item.valid()) {
                results.add(new InstrumentImportResult(null, null, null, "FAILED", "FAILED",
                        "SKIPPED", 0, 0, null, item.message()));
                continue;
            }
            try {
                results.add(importOne(item.symbol(), request.syncDailyPrices(), request.startDate()));
            } catch (Exception ex) {
                results.add(new InstrumentImportResult(item.symbol().canonicalCode(), null, null, "FAILED",
                        "FAILED", "SKIPPED", 0, 0, null, safeMessage(ex)));
            }
        }
        return results;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InstrumentImportResult importOne(MarketSymbol symbol, boolean syncDailyPrices, LocalDate requestedStartDate) {
        ProviderInstrumentMetadata metadata = null;
        String providerErrorMessage = null;
        try {
            metadata = lookupMetadata(List.of(symbol)).get(symbol.canonicalCode());
        } catch (MarketDataProviderException ex) {
            providerErrorMessage = ex.getMessage();
        }
        boolean metadataFallback = metadata == null;
        metadata = metadataOrFallback(symbol, metadata);

        SecurityInstrument existing = findExisting(symbol);
        LocalDateTime now = LocalDateTime.now(CHINA_ZONE);
        String action;
        SecurityInstrument instrument = existing == null ? new SecurityInstrument() : existing;
        if (existing == null) {
            instrument.setSymbol(symbol.symbol());
            instrument.setExchange(symbol.exchange());
            instrument.setCreatedAt(now);
            action = "CREATED";
        } else {
            action = hasMetadataChanged(existing, metadata, symbol) ? "UPDATED" : "UNCHANGED";
        }

        instrument.setInstrumentName(metadata.instrumentName());
        instrument.setAssetType(AssetType.STOCK);
        instrument.setCurrency("CNY");
        instrument.setLotSize(lotSizeFor(symbol));
        instrument.setStatus("L".equals(metadata.listStatus()) ? InstrumentStatus.ACTIVE : InstrumentStatus.DISABLED);
        instrument.setUpdatedAt(now);

        if (existing == null) {
            securityInstrumentMapper.insert(instrument);
        } else if (!"UNCHANGED".equals(action)) {
            securityInstrumentMapper.updateById(instrument);
        }

        DailyPriceSyncResult syncResult = null;
        if (syncDailyPrices) {
            LocalDate startDate = requestedStartDate == null ? properties.getDefaultStartDate() : requestedStartDate;
            if (metadata.listDate() != null && metadata.listDate().isAfter(startDate)) {
                startDate = metadata.listDate();
            }
            syncResult = syncInstrumentDailyPrices(instrument.getId(),
                    new DailyPriceSyncRequest(startDate, currentChinaDate(), false), "IMPORT");
        }

        return new InstrumentImportResult(symbol.canonicalCode(), instrument.getId(), instrument.getInstrumentName(),
                action, metadataFallback ? "FALLBACK" : "SUCCESS", syncResult == null ? "SKIPPED" : syncResult.status(),
                syncResult == null ? 0 : syncResult.recordsInserted(),
                syncResult == null ? 0 : syncResult.recordsUpdated(),
                syncResult == null ? latestTradeDate(instrument.getId()) : syncResult.latestTradeDate(),
                metadataFallback ? metadataFallbackMessage(providerErrorMessage) : "OK");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyPriceSyncResult syncInstrumentDailyPrices(Long instrumentId, DailyPriceSyncRequest request, String syncType) {
        SecurityInstrument instrument = requireInstrument(instrumentId);
        if (!isSupportedImportedInstrument(instrument)) {
            return new DailyPriceSyncResult(instrumentId, canonicalCode(instrument), "FAILED",
                    0, 0, 0, latestTradeDate(instrumentId),
                    "Only ACTIVE SSE/SZSE STOCK instruments are supported");
        }

        LocalDate endDate = request != null && request.endDate() != null ? request.endDate() : currentChinaDate();
        LocalDate startDate = resolveSyncStartDate(instrumentId, request, endDate);
        MarketDataSyncLog log = startLog(instrumentId, syncType, startDate, endDate);
        try {
            if (startDate.isAfter(endDate)) {
                finishLog(log, MarketDataSyncStatus.SUCCESS, 0, 0, 0, null);
                return new DailyPriceSyncResult(instrumentId, canonicalCode(instrument), "SUCCESS",
                        0, 0, 0, latestTradeDate(instrumentId), "Already up to date");
            }
            List<ProviderDailyBar> bars = marketDataProvider.fetchDailyBars(
                    List.of(new MarketSymbol(instrument.getSymbol(), instrument.getExchange())), startDate, endDate);
            int inserted = 0;
            int updated = 0;
            for (ProviderDailyBar bar : bars) {
                validateBar(bar);
                UpsertCount count = upsertDailyBar(instrumentId, bar);
                inserted += count.inserted();
                updated += count.updated();
            }
            finishLog(log, MarketDataSyncStatus.SUCCESS, bars.size(), inserted, updated, null);
            return new DailyPriceSyncResult(instrumentId, canonicalCode(instrument), "SUCCESS",
                    bars.size(), inserted, updated, latestTradeDate(instrumentId), "OK");
        } catch (Exception ex) {
            finishLog(log, MarketDataSyncStatus.FAILED, 0, 0, 0, safeMessage(ex));
            return new DailyPriceSyncResult(instrumentId, canonicalCode(instrument), "FAILED",
                    0, 0, 0, latestTradeDate(instrumentId), safeMessage(ex));
        }
    }

    @Override
    public BatchDailyPriceSyncResult syncDailyPrices(BatchDailyPriceSyncRequest request, String syncType) {
        List<Long> ids = resolveBatchIds(request);
        List<DailyPriceSyncResult> items = new ArrayList<>();
        for (Long id : ids) {
            items.add(syncInstrumentDailyPrices(id, new DailyPriceSyncRequest(null, null, false), syncType));
        }
        int success = (int) items.stream().filter(item -> "SUCCESS".equals(item.status())).count();
        int failed = (int) items.stream().filter(item -> "FAILED".equals(item.status())).count();
        int skipped = (int) items.stream().filter(item -> "SKIPPED".equals(item.status())).count();
        return new BatchDailyPriceSyncResult(success, failed, skipped, items);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyPriceResponse> listDailyPrices(Long instrumentId, LocalDate from, LocalDate to, Integer limit) {
        requireInstrument(instrumentId);
        LocalDate end = to == null ? currentChinaDate() : to;
        LocalDate start = from == null ? end.minusYears(1) : from;
        if (start.isAfter(end)) {
            throw new BusinessRuleException("from must not be later than to");
        }
        if (start.plusYears(MAX_RANGE_YEARS).isBefore(end)) {
            throw new BusinessRuleException("Date range must not exceed 30 years");
        }
        int size = limit == null ? DEFAULT_DAILY_PRICE_LIMIT : limit;
        if (size <= 0 || size > MAX_DAILY_PRICE_LIMIT) {
            throw new BusinessRuleException("limit must be between 1 and " + MAX_DAILY_PRICE_LIMIT);
        }
        return dailyPriceMapper.selectList(Wrappers.<InstrumentDailyPrice>lambdaQuery()
                        .eq(InstrumentDailyPrice::getInstrumentId, instrumentId)
                        .ge(InstrumentDailyPrice::getTradeDate, start)
                        .le(InstrumentDailyPrice::getTradeDate, end)
                        .orderByAsc(InstrumentDailyPrice::getTradeDate)
                        .last("LIMIT " + size))
                .stream()
                .map(DailyPriceResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DailyPriceSummaryResponse getDailyPriceSummary(Long instrumentId) {
        requireInstrument(instrumentId);
        List<InstrumentDailyPrice> prices = dailyPriceMapper.selectList(Wrappers.<InstrumentDailyPrice>lambdaQuery()
                .eq(InstrumentDailyPrice::getInstrumentId, instrumentId)
                .orderByAsc(InstrumentDailyPrice::getTradeDate));
        if (prices.isEmpty()) {
            LocalDateTime lastSync = lastSyncAt(instrumentId);
            return new DailyPriceSummaryResponse(instrumentId, null, null, 0,
                    null, null, null, null, lastSync, marketDataProvider.providerName());
        }
        InstrumentDailyPrice latest = prices.getLast();
        BigDecimal high = prices.stream().map(InstrumentDailyPrice::getHighPrice).max(Comparator.naturalOrder()).orElse(null);
        BigDecimal low = prices.stream().map(InstrumentDailyPrice::getLowPrice).min(Comparator.naturalOrder()).orElse(null);
        return new DailyPriceSummaryResponse(instrumentId, prices.getFirst().getTradeDate(), latest.getTradeDate(),
                prices.size(), latest.getClosePrice(), latest.getPercentageChange(), high, low,
                lastSyncAt(instrumentId), latest.getDataSource());
    }

    @Override
    @Transactional
    public void clearDailyPrices(Long instrumentId) {
        requireInstrument(instrumentId);
        if (hasRunningSync(instrumentId)) {
            throw new BusinessRuleException("Security instrument has a running market data sync task");
        }
        dailyPriceMapper.delete(Wrappers.<InstrumentDailyPrice>lambdaQuery()
                .eq(InstrumentDailyPrice::getInstrumentId, instrumentId));
        syncLogMapper.delete(Wrappers.<MarketDataSyncLog>lambdaQuery()
                .eq(MarketDataSyncLog::getInstrumentId, instrumentId));
    }

    private Map<String, ProviderInstrumentMetadata> lookupMetadata(List<MarketSymbol> symbols) {
        if (symbols.isEmpty()) {
            return Map.of();
        }
        return marketDataProvider.lookupInstruments(symbols).stream()
                .collect(Collectors.toMap(item -> item.marketSymbol().canonicalCode(), Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
    }

    private ProviderInstrumentMetadata metadataOrFallback(MarketSymbol symbol, ProviderInstrumentMetadata metadata) {
        if (metadata != null) {
            return metadata;
        }
        return new ProviderInstrumentMetadata(symbol, symbol.canonicalCode(), symbol.exchange(), "CNY", "L", null);
    }

    private String metadataFallbackMessage(String providerErrorMessage) {
        if (providerErrorMessage == null || providerErrorMessage.isBlank()) {
            return "Instrument metadata unavailable; using canonical code as instrument name";
        }
        return "Instrument metadata unavailable; using canonical code as instrument name. Provider message: "
                + providerErrorMessage;
    }

    private SecurityInstrument findExisting(MarketSymbol symbol) {
        return securityInstrumentMapper.selectOne(Wrappers.<SecurityInstrument>lambdaQuery()
                .eq(SecurityInstrument::getExchange, symbol.exchange())
                .eq(SecurityInstrument::getSymbol, symbol.symbol()));
    }

    private boolean hasMetadataChanged(SecurityInstrument existing, ProviderInstrumentMetadata metadata, MarketSymbol symbol) {
        return !Objects.equals(existing.getInstrumentName(), metadata.instrumentName())
                || existing.getExchange() != symbol.exchange()
                || existing.getAssetType() != AssetType.STOCK
                || !Objects.equals(existing.getCurrency(), "CNY")
                || existing.getLotSize().compareTo(lotSizeFor(symbol)) != 0
                || existing.getStatus() != ("L".equals(metadata.listStatus()) ? InstrumentStatus.ACTIVE : InstrumentStatus.DISABLED);
    }

    private BigDecimal lotSizeFor(MarketSymbol symbol) {
        if (symbol.exchange() == Exchange.SSE && symbol.symbol().startsWith("688")) {
            return new BigDecimal("200.000000");
        }
        return new BigDecimal("100.000000");
    }

    private SecurityInstrument requireInstrument(Long instrumentId) {
        SecurityInstrument instrument = securityInstrumentMapper.selectById(instrumentId);
        if (instrument == null) {
            throw new ResourceNotFoundException("Security instrument not found");
        }
        return instrument;
    }

    private boolean isSupportedImportedInstrument(SecurityInstrument instrument) {
        return instrument.getStatus() == InstrumentStatus.ACTIVE
                && instrument.getAssetType() == AssetType.STOCK
                && (instrument.getExchange() == Exchange.SSE || instrument.getExchange() == Exchange.SZSE);
    }

    private boolean hasRunningSync(Long instrumentId) {
        return syncLogMapper.exists(Wrappers.<MarketDataSyncLog>lambdaQuery()
                .eq(MarketDataSyncLog::getInstrumentId, instrumentId)
                .eq(MarketDataSyncLog::getStatus, MarketDataSyncStatus.RUNNING));
    }

    private LocalDate resolveSyncStartDate(Long instrumentId, DailyPriceSyncRequest request, LocalDate endDate) {
        if (request != null && request.startDate() != null) {
            return request.startDate();
        }
        if (request != null && request.fullRefresh()) {
            return properties.getDefaultStartDate();
        }
        LocalDate latest = latestTradeDate(instrumentId);
        if (latest != null) {
            return latest.plusDays(1);
        }
        return properties.getDefaultStartDate();
    }

    private UpsertCount upsertDailyBar(Long instrumentId, ProviderDailyBar bar) {
        InstrumentDailyPrice existing = dailyPriceMapper.selectOne(Wrappers.<InstrumentDailyPrice>lambdaQuery()
                .eq(InstrumentDailyPrice::getInstrumentId, instrumentId)
                .eq(InstrumentDailyPrice::getTradeDate, bar.tradeDate())
                .eq(InstrumentDailyPrice::getAdjustmentType, AdjustmentType.NONE));
        InstrumentDailyPrice price = existing == null ? new InstrumentDailyPrice() : existing;
        LocalDateTime now = LocalDateTime.now(CHINA_ZONE);
        price.setInstrumentId(instrumentId);
        price.setTradeDate(bar.tradeDate());
        price.setOpenPrice(bar.open());
        price.setHighPrice(bar.high());
        price.setLowPrice(bar.low());
        price.setClosePrice(bar.close());
        price.setPreviousClose(bar.previousClose());
        price.setPriceChange(bar.change());
        price.setPercentageChange(bar.percentageChange());
        price.setVolumeLots(bar.volumeLots());
        price.setAmountCny(bar.amountCny());
        price.setAdjustmentType(AdjustmentType.NONE);
        price.setDataSource(marketDataProvider.providerName());
        price.setUpdatedAt(now);
        if (existing == null) {
            price.setCreatedAt(now);
            price.setVersion(0);
            dailyPriceMapper.insert(price);
            return new UpsertCount(1, 0);
        }
        dailyPriceMapper.updateById(price);
        return new UpsertCount(0, 1);
    }

    private void validateBar(ProviderDailyBar bar) {
        if (bar.open().signum() <= 0 || bar.high().signum() <= 0 || bar.low().signum() <= 0 || bar.close().signum() <= 0) {
            throw new BusinessRuleException("Invalid OHLC: prices must be positive");
        }
        if (bar.high().compareTo(bar.open()) < 0 || bar.high().compareTo(bar.close()) < 0
                || bar.high().compareTo(bar.low()) < 0 || bar.low().compareTo(bar.open()) > 0
                || bar.low().compareTo(bar.close()) > 0) {
            throw new BusinessRuleException("Invalid OHLC: high/low relationship is inconsistent");
        }
        if (bar.volumeLots().signum() < 0 || bar.amountCny().signum() < 0) {
            throw new BusinessRuleException("Invalid volume or amount");
        }
    }

    private MarketDataSyncLog startLog(Long instrumentId, String syncType, LocalDate start, LocalDate end) {
        MarketDataSyncLog log = new MarketDataSyncLog();
        log.setInstrumentId(instrumentId);
        log.setProvider(marketDataProvider.providerName());
        log.setSyncType(syncType);
        log.setRequestedStartDate(start);
        log.setRequestedEndDate(end);
        log.setRecordsReceived(0);
        log.setRecordsInserted(0);
        log.setRecordsUpdated(0);
        log.setStatus(MarketDataSyncStatus.RUNNING);
        log.setStartedAt(LocalDateTime.now(CHINA_ZONE));
        syncLogMapper.insert(log);
        return log;
    }

    private void finishLog(MarketDataSyncLog log, MarketDataSyncStatus status, int received, int inserted,
                           int updated, String errorMessage) {
        log.setStatus(status);
        log.setRecordsReceived(received);
        log.setRecordsInserted(inserted);
        log.setRecordsUpdated(updated);
        log.setErrorMessage(errorMessage == null ? null : errorMessage.substring(0, Math.min(512, errorMessage.length())));
        log.setFinishedAt(LocalDateTime.now(CHINA_ZONE));
        syncLogMapper.updateById(log);
    }

    private List<Long> resolveBatchIds(BatchDailyPriceSyncRequest request) {
        if (request == null) {
            throw new BusinessRuleException("Request body is required");
        }
        if (request.instrumentIds() != null && !request.instrumentIds().isEmpty()) {
            return request.instrumentIds();
        }
        if (request.syncAllImportedInstruments()) {
            return securityInstrumentMapper.selectList(Wrappers.<SecurityInstrument>lambdaQuery()
                            .eq(SecurityInstrument::getStatus, InstrumentStatus.ACTIVE)
                            .eq(SecurityInstrument::getAssetType, AssetType.STOCK)
                            .in(SecurityInstrument::getExchange, List.of(Exchange.SSE, Exchange.SZSE))
                            .orderByAsc(SecurityInstrument::getExchange)
                            .orderByAsc(SecurityInstrument::getSymbol))
                    .stream()
                    .map(SecurityInstrument::getId)
                    .toList();
        }
        throw new BusinessRuleException("instrumentIds or syncAllImportedInstruments=true is required");
    }

    private LocalDate latestTradeDate(Long instrumentId) {
        InstrumentDailyPrice latest = dailyPriceMapper.selectOne(Wrappers.<InstrumentDailyPrice>lambdaQuery()
                .eq(InstrumentDailyPrice::getInstrumentId, instrumentId)
                .orderByDesc(InstrumentDailyPrice::getTradeDate)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getTradeDate();
    }

    private LocalDateTime lastSyncAt(Long instrumentId) {
        MarketDataSyncLog latest = syncLogMapper.selectOne(Wrappers.<MarketDataSyncLog>lambdaQuery()
                .eq(MarketDataSyncLog::getInstrumentId, instrumentId)
                .orderByDesc(MarketDataSyncLog::getStartedAt)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getFinishedAt();
    }

    private String canonicalCode(SecurityInstrument instrument) {
        return new MarketSymbol(instrument.getSymbol(), instrument.getExchange()).canonicalCode();
    }

    private LocalDate currentChinaDate() {
        return LocalDate.now(CHINA_ZONE);
    }

    private String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private record UpsertCount(int inserted, int updated) {
    }
}
