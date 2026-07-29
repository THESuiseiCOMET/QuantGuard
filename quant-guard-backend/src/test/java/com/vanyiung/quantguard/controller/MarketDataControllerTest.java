package com.vanyiung.quantguard.controller;

import com.vanyiung.quantguard.marketdata.config.MarketDataProperties;
import com.vanyiung.quantguard.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MarketDataControllerTest {

    private MarketDataProperties properties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        properties = new MarketDataProperties();
        mockMvc = MockMvcBuilders.standaloneSetup(new MarketDataController(mock(MarketDataService.class), properties))
                .build();
    }

    @Test
    void configReportsTokenStatusWithoutReturningToken() throws Exception {
        properties.setToken("secret-token");

        mockMvc.perform(get("/api/market-data/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider", is("python")))
                .andExpect(jsonPath("$.data.tokenConfigured", is(true)))
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    void configUpdateAcceptsTushareTokenAlias() throws Exception {
        mockMvc.perform(put("/api/market-data/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tushareToken":"new-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenConfigured", is(true)));
    }
}
