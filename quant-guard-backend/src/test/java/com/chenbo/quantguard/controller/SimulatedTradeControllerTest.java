package com.chenbo.quantguard.controller;

import com.chenbo.quantguard.dto.ExecuteSimulatedOrderResponse;
import com.chenbo.quantguard.dto.SimulatedOrderResponse;
import com.chenbo.quantguard.dto.SimulatedTradeResponse;
import com.chenbo.quantguard.enums.OrderSide;
import com.chenbo.quantguard.enums.OrderStatus;
import com.chenbo.quantguard.enums.OrderType;
import com.chenbo.quantguard.exception.BusinessRuleException;
import com.chenbo.quantguard.exception.GlobalExceptionHandler;
import com.chenbo.quantguard.exception.ResourceNotFoundException;
import com.chenbo.quantguard.service.CashLedgerService;
import com.chenbo.quantguard.service.SimulatedOrderExecutionService;
import com.chenbo.quantguard.service.SimulatedTradeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SimulatedTradeControllerTest {

    private MockMvc mockMvc;
    private SimulatedOrderExecutionService simulatedOrderExecutionService;
    private SimulatedTradeService simulatedTradeService;
    private CashLedgerService cashLedgerService;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        simulatedOrderExecutionService = mock(SimulatedOrderExecutionService.class);
        simulatedTradeService = mock(SimulatedTradeService.class);
        cashLedgerService = mock(CashLedgerService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new SimulatedTradeController(
                        simulatedOrderExecutionService,
                        simulatedTradeService,
                        cashLedgerService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new JacksonJsonHttpMessageConverter())
                .build();
    }

    @Test
    void executeOrderSucceeds() throws Exception {
        when(simulatedOrderExecutionService.execute(100L)).thenReturn(new ExecuteSimulatedOrderResponse(order(OrderStatus.FILLED), trade()));

        mockMvc.perform(post("/api/orders/100/execute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.order.status").value("FILLED"))
                .andExpect(jsonPath("$.data.trade.id").value(200));
    }

    @Test
    void cancelOrderSucceeds() throws Exception {
        when(simulatedOrderExecutionService.cancel(100L)).thenReturn(order(OrderStatus.CANCELED));

        mockMvc.perform(post("/api/orders/100/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    void zeroOrderIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders/0/execute"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void negativeOrderIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders/-1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void nonNumericOrderIdReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/orders/not-a-number/execute"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid path parameter: id"));
    }

    @Test
    void missingOrderReturnsNotFound() throws Exception {
        when(simulatedOrderExecutionService.execute(999L)).thenThrow(new ResourceNotFoundException("Simulated order not found"));

        mockMvc.perform(post("/api/orders/999/execute"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Simulated order not found"));
    }

    @Test
    void nonOpenOrderReturnsBusinessError() throws Exception {
        when(simulatedOrderExecutionService.cancel(100L)).thenThrow(new BusinessRuleException("Only OPEN orders can be operated"));

        mockMvc.perform(post("/api/orders/100/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Only OPEN orders can be operated"));
    }

    @Test
    void unexpectedServiceExceptionReturnsUnifiedResponse() throws Exception {
        when(simulatedOrderExecutionService.execute(100L)).thenThrow(new IllegalStateException("boom"));

        mockMvc.perform(post("/api/orders/100/execute"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }

    @Test
    void emptyTradeListReturnsOkAndEmptyArray() throws Exception {
        when(simulatedTradeService.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private SimulatedOrderResponse order(OrderStatus status) {
        return new SimulatedOrderResponse(
                100L,
                "SO20260724000000001",
                1L,
                1L,
                1L,
                OrderSide.BUY,
                OrderType.LIMIT,
                new BigDecimal("200.000000"),
                new BigDecimal("10.0000"),
                status == OrderStatus.CANCELED ? BigDecimal.ZERO.setScale(4) : new BigDecimal("2000.0000"),
                BigDecimal.ZERO.setScale(6),
                status,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private SimulatedTradeResponse trade() {
        LocalDateTime now = LocalDateTime.now();
        return new SimulatedTradeResponse(
                200L,
                "ST20260724000000001",
                100L,
                1L,
                1L,
                1L,
                OrderSide.BUY,
                new BigDecimal("200.000000"),
                new BigDecimal("10.0000"),
                new BigDecimal("2000.0000"),
                now,
                now
        );
    }
}
