package com.vanyiung.quantguard.controller;

import com.vanyiung.quantguard.dto.BatchDailyPriceSyncRequest;
import com.vanyiung.quantguard.dto.BatchDailyPriceSyncResult;
import com.vanyiung.quantguard.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketDataControllerTest {

    private MarketDataService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(MarketDataService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketDataController(service)).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/market-data/config", "/api/market-data/provider-config"})
    void removedConfigurationEndpointsAreUnavailable(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isNotFound());
        mockMvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test
    void batchSyncStillDelegatesToService() throws Exception {
        BatchDailyPriceSyncRequest request = new BatchDailyPriceSyncRequest(List.of(1L), false);
        when(service.syncDailyPrices(request, "BATCH"))
                .thenReturn(new BatchDailyPriceSyncResult(1, 0, 0, List.of()));

        mockMvc.perform(post("/api/market-data/daily-prices/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instrumentIds\":[1],\"syncAllImportedInstruments\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount", is(1)));
        verify(service).syncDailyPrices(request, "BATCH");
    }
}
