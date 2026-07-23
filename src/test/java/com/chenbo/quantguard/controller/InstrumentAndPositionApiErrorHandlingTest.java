package com.chenbo.quantguard.controller;

import com.chenbo.quantguard.exception.GlobalExceptionHandler;
import com.chenbo.quantguard.exception.ResourceNotFoundException;
import com.chenbo.quantguard.service.PortfolioPositionService;
import com.chenbo.quantguard.service.SecurityInstrumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InstrumentAndPositionApiErrorHandlingTest {

    private MockMvc instrumentMockMvc;
    private MockMvc positionMockMvc;
    private SecurityInstrumentService securityInstrumentService;
    private PortfolioPositionService portfolioPositionService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        securityInstrumentService = mock(SecurityInstrumentService.class);
        portfolioPositionService = mock(PortfolioPositionService.class);

        instrumentMockMvc = MockMvcBuilders.standaloneSetup(new SecurityInstrumentController(securityInstrumentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();

        positionMockMvc = MockMvcBuilders.standaloneSetup(new PortfolioPositionController(portfolioPositionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    void invalidExchangeReturnsBadRequest() throws Exception {
        instrumentMockMvc.perform(post("/api/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "AAPL",
                                  "exchange": "INVALID",
                                  "instrumentName": "Apple Inc",
                                  "assetType": "STOCK",
                                  "currency": "USD",
                                  "lotSize": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request body"));
    }

    @Test
    void invalidAssetTypeReturnsBadRequest() throws Exception {
        instrumentMockMvc.perform(post("/api/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "AAPL",
                                  "exchange": "NASDAQ",
                                  "instrumentName": "Apple Inc",
                                  "assetType": "INVALID",
                                  "currency": "USD",
                                  "lotSize": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request body"));
    }

    @Test
    void nonPositiveLotSizeReturnsBadRequest() throws Exception {
        instrumentMockMvc.perform(post("/api/instruments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "symbol": "AAPL",
                                  "exchange": "NASDAQ",
                                  "instrumentName": "Apple Inc",
                                  "assetType": "STOCK",
                                  "currency": "USD",
                                  "lotSize": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("lotSize")));
    }

    @Test
    void missingInstrumentReturnsNotFound() throws Exception {
        when(securityInstrumentService.getById(999L)).thenThrow(new ResourceNotFoundException("Security instrument not found"));

        instrumentMockMvc.perform(get("/api/instruments/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Security instrument not found"));
    }

    @Test
    void missingPositionReturnsNotFound() throws Exception {
        when(portfolioPositionService.getById(999L)).thenThrow(new ResourceNotFoundException("Portfolio position not found"));

        positionMockMvc.perform(get("/api/positions/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Portfolio position not found"));
    }
}
