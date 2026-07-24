package com.vanyiung.quantguard.controller;

import com.vanyiung.quantguard.exception.GlobalExceptionHandler;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.exception.DuplicateResourceException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.service.InvestmentPortfolioService;
import com.vanyiung.quantguard.service.SimulationAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiErrorHandlingTest {

    private MockMvc accountMockMvc;
    private MockMvc portfolioMockMvc;
    private SimulationAccountService simulationAccountService;
    private InvestmentPortfolioService investmentPortfolioService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        simulationAccountService = mock(SimulationAccountService.class);
        investmentPortfolioService = mock(InvestmentPortfolioService.class);

        accountMockMvc = MockMvcBuilders.standaloneSetup(new SimulationAccountController(simulationAccountService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();

        portfolioMockMvc = MockMvcBuilders.standaloneSetup(new InvestmentPortfolioController(investmentPortfolioService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    void invalidAccountTypeReturnsBadRequest() throws Exception {
        accountMockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "Long ETF",
                                  "accountType": "INVALID",
                                  "initialCash": 100000.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request body"));
    }

    @Test
    void negativeInitialCashReturnsBadRequest() throws Exception {
        accountMockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "Long ETF",
                                  "accountType": "LONG_TERM",
                                  "initialCash": -1.00
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("initialCash")));
    }

    @Test
    void nonNumericAccountIdReturnsBadRequest() throws Exception {
        accountMockMvc.perform(get("/api/accounts/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid path parameter: id"));
    }

    @Test
    void missingAccountReturnsNotFound() throws Exception {
        when(simulationAccountService.getById(999L)).thenThrow(new ResourceNotFoundException("Simulation account not found"));

        accountMockMvc.perform(get("/api/accounts/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Simulation account not found"));
    }

    @Test
    void duplicateAccountNameReturnsConflict() throws Exception {
        when(simulationAccountService.create(any())).thenThrow(new DuplicateResourceException("Simulation account name already exists"));

        accountMockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountName": "Long ETF",
                                  "accountType": "LONG_TERM",
                                  "initialCash": 100000.00
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Simulation account name already exists"));
    }

    @Test
    void invalidPortfolioTypeReturnsBadRequest() throws Exception {
        portfolioMockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": 1,
                                  "portfolioName": "Core ETF",
                                  "portfolioType": "INVALID"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid request body"));
    }

    @Test
    void missingPortfolioReturnsNotFound() throws Exception {
        when(investmentPortfolioService.getById(999L)).thenThrow(new ResourceNotFoundException("Investment portfolio not found"));

        portfolioMockMvc.perform(get("/api/portfolios/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Investment portfolio not found"));
    }

    @Test
    void duplicatePortfolioNameReturnsConflict() throws Exception {
        when(investmentPortfolioService.create(any()))
                .thenThrow(new DuplicateResourceException("Portfolio name already exists in this account"));

        portfolioMockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": 1,
                                  "portfolioName": "Core ETF",
                                  "portfolioType": "LONG_TERM"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Portfolio name already exists in this account"));
    }

    @Test
    void portfolioTypeMismatchReturnsBadRequest() throws Exception {
        when(investmentPortfolioService.create(any()))
                .thenThrow(new BusinessRuleException("Portfolio type must match simulation account type"));

        portfolioMockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": 1,
                                  "portfolioName": "Short Trade",
                                  "portfolioType": "SHORT_TERM"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Portfolio type must match simulation account type"));
    }
}
