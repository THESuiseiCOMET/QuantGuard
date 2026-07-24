package com.chenbo.quantguard.controller;

import com.chenbo.quantguard.exception.GlobalExceptionHandler;
import com.chenbo.quantguard.service.PortfolioRiskConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioRiskConfigControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        PortfolioRiskConfigService portfolioRiskConfigService = mock(PortfolioRiskConfigService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new PortfolioRiskConfigController(portfolioRiskConfigService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    void invalidUpdateRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/risk-configs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "maxSingleOrderAmount": 0,
                                  "maxPositionRatio": 1.200000,
                                  "allowBuy": true,
                                  "allowSell": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("maxSingleOrderAmount")));
    }
}
