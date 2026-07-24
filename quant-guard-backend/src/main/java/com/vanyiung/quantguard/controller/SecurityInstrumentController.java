package com.vanyiung.quantguard.controller;

import com.vanyiung.quantguard.common.ApiResponse;
import com.vanyiung.quantguard.dto.CreateSecurityInstrumentRequest;
import com.vanyiung.quantguard.dto.DailyPriceResponse;
import com.vanyiung.quantguard.dto.DailyPriceSummaryResponse;
import com.vanyiung.quantguard.dto.DailyPriceSyncRequest;
import com.vanyiung.quantguard.dto.DailyPriceSyncResult;
import com.vanyiung.quantguard.dto.ImportInstrumentsRequest;
import com.vanyiung.quantguard.dto.InstrumentImportPreviewItem;
import com.vanyiung.quantguard.dto.InstrumentImportPreviewRequest;
import com.vanyiung.quantguard.dto.InstrumentImportResult;
import com.vanyiung.quantguard.dto.SecurityInstrumentResponse;
import com.vanyiung.quantguard.service.MarketDataService;
import com.vanyiung.quantguard.service.SecurityInstrumentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/instruments")
public class SecurityInstrumentController {

    private final SecurityInstrumentService securityInstrumentService;
    private final MarketDataService marketDataService;

    public SecurityInstrumentController(SecurityInstrumentService securityInstrumentService,
                                        MarketDataService marketDataService) {
        this.securityInstrumentService = securityInstrumentService;
        this.marketDataService = marketDataService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SecurityInstrumentResponse>> create(@Valid @RequestBody CreateSecurityInstrumentRequest request) {
        SecurityInstrumentResponse response = securityInstrumentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<List<SecurityInstrumentResponse>> listAll() {
        return ApiResponse.success(securityInstrumentService.listAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<SecurityInstrumentResponse> getById(@PathVariable @Positive(message = "id must be positive") Long id) {
        return ApiResponse.success(securityInstrumentService.getById(id));
    }

    @PostMapping("/import/preview")
    public ApiResponse<List<InstrumentImportPreviewItem>> previewImport(
            @Valid @RequestBody InstrumentImportPreviewRequest request) {
        return ApiResponse.success(marketDataService.previewImport(request));
    }

    @PostMapping("/import")
    public ApiResponse<List<InstrumentImportResult>> importInstruments(
            @Valid @RequestBody ImportInstrumentsRequest request) {
        return ApiResponse.success(marketDataService.importInstruments(request));
    }

    @PostMapping("/{id}/daily-prices/sync")
    public ApiResponse<DailyPriceSyncResult> syncDailyPrices(
            @PathVariable @Positive(message = "id must be positive") Long id,
            @RequestBody(required = false) DailyPriceSyncRequest request) {
        DailyPriceSyncRequest syncRequest = request == null ? new DailyPriceSyncRequest(null, null, false) : request;
        return ApiResponse.success(marketDataService.syncInstrumentDailyPrices(id, syncRequest, "MANUAL"));
    }

    @GetMapping("/{id}/daily-prices")
    public ApiResponse<List<DailyPriceResponse>> listDailyPrices(
            @PathVariable @Positive(message = "id must be positive") Long id,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(marketDataService.listDailyPrices(id, from, to, limit));
    }

    @GetMapping("/{id}/daily-prices/summary")
    public ApiResponse<DailyPriceSummaryResponse> getDailyPriceSummary(
            @PathVariable @Positive(message = "id must be positive") Long id) {
        return ApiResponse.success(marketDataService.getDailyPriceSummary(id));
    }
}
