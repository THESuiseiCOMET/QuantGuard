package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CreateSimulatedOrderRequest;
import com.vanyiung.quantguard.dto.SimulatedOrderResponse;
import com.vanyiung.quantguard.entity.CashLedger;
import com.vanyiung.quantguard.entity.InvestmentPortfolio;
import com.vanyiung.quantguard.entity.PortfolioPosition;
import com.vanyiung.quantguard.entity.PortfolioRiskConfig;
import com.vanyiung.quantguard.entity.SecurityInstrument;
import com.vanyiung.quantguard.entity.SimulatedOrder;
import com.vanyiung.quantguard.entity.SimulationAccount;
import com.vanyiung.quantguard.enums.AccountStatus;
import com.vanyiung.quantguard.enums.AccountType;
import com.vanyiung.quantguard.enums.AssetType;
import com.vanyiung.quantguard.enums.CashLedgerEntryType;
import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.enums.InstrumentStatus;
import com.vanyiung.quantguard.enums.OrderSide;
import com.vanyiung.quantguard.enums.OrderStatus;
import com.vanyiung.quantguard.enums.OrderType;
import com.vanyiung.quantguard.enums.RiskConfigStatus;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.mapper.CashLedgerMapper;
import com.vanyiung.quantguard.mapper.InvestmentPortfolioMapper;
import com.vanyiung.quantguard.mapper.PortfolioPositionMapper;
import com.vanyiung.quantguard.mapper.PortfolioRiskConfigMapper;
import com.vanyiung.quantguard.mapper.SecurityInstrumentMapper;
import com.vanyiung.quantguard.mapper.SimulatedOrderMapper;
import com.vanyiung.quantguard.mapper.SimulationAccountMapper;
import com.vanyiung.quantguard.service.impl.SimulatedOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulatedOrderServiceImplTest {

    @Mock
    private SimulatedOrderMapper simulatedOrderMapper;

    @Mock
    private SimulationAccountMapper simulationAccountMapper;

    @Mock
    private InvestmentPortfolioMapper investmentPortfolioMapper;

    @Mock
    private SecurityInstrumentMapper securityInstrumentMapper;

    @Mock
    private PortfolioRiskConfigMapper portfolioRiskConfigMapper;

    @Mock
    private PortfolioPositionMapper portfolioPositionMapper;

    @Mock
    private CashLedgerMapper cashLedgerMapper;

    private SimulatedOrderServiceImpl simulatedOrderService;

    @BeforeEach
    void setUp() {
        simulatedOrderService = new SimulatedOrderServiceImpl(
                simulatedOrderMapper,
                simulationAccountMapper,
                investmentPortfolioMapper,
                securityInstrumentMapper,
                portfolioRiskConfigMapper,
                portfolioPositionMapper,
                cashLedgerMapper);
    }

    @Test
    void buyOrderSucceedsAndFreezesCash() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")), riskConfig());
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedOrderMapper.insert(any(SimulatedOrder.class))).thenAnswer(invocation -> {
            SimulatedOrder order = invocation.getArgument(0);
            order.setId(100L);
            return 1;
        });
        when(cashLedgerMapper.insert(any(CashLedger.class))).thenReturn(1);

        SimulatedOrderResponse response = simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000")));

        assertEquals(OrderStatus.OPEN, response.status());
        assertEquals(new BigDecimal("2000.0000"), response.frozenAmount());
        verify(simulationAccountMapper).updateById(any(SimulationAccount.class));
        verify(portfolioPositionMapper, never()).updateById(any(PortfolioPosition.class));

        ArgumentCaptor<CashLedger> ledgerCaptor = ArgumentCaptor.forClass(CashLedger.class);
        verify(cashLedgerMapper).insert(ledgerCaptor.capture());
        CashLedger ledger = ledgerCaptor.getValue();
        assertEquals(100L, ledger.getOrderId());
        assertEquals(CashLedgerEntryType.BUY_ORDER_FREEZE, ledger.getEntryType());
        assertEquals(new BigDecimal("-2000.0000"), ledger.getAvailableCashDelta());
        assertEquals(new BigDecimal("2000.0000"), ledger.getFrozenCashDelta());
        assertEquals(new BigDecimal("98000.0000"), ledger.getAvailableCashAfter());
        assertEquals(new BigDecimal("2000.0000"), ledger.getFrozenCashAfter());
    }

    @Test
    void buyOrderRejectsTinyAmountThatWouldRoundToZero() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("0.000001")), riskConfig());

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("0.000001"), new BigDecimal("0.0001"))));

        verify(simulationAccountMapper, never()).updateById(any(SimulationAccount.class));
        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void buyOrderAllowsMinimumAmount() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("1.000000")), riskConfig());
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedOrderMapper.insert(any(SimulatedOrder.class))).thenAnswer(invocation -> {
            SimulatedOrder order = invocation.getArgument(0);
            order.setId(102L);
            return 1;
        });
        when(cashLedgerMapper.insert(any(CashLedger.class))).thenReturn(1);

        SimulatedOrderResponse response = simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("1.000000"), new BigDecimal("0.0001")));

        assertEquals(new BigDecimal("0.0001"), response.frozenAmount());
    }

    @Test
    void buyOrderFreezesUsingUnifiedFourDecimalAmount() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("0.000001")), riskConfig());
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedOrderMapper.insert(any(SimulatedOrder.class))).thenAnswer(invocation -> {
            SimulatedOrder order = invocation.getArgument(0);
            order.setId(103L);
            return 1;
        });
        when(cashLedgerMapper.insert(any(CashLedger.class))).thenReturn(1);

        SimulatedOrderResponse response = simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("1.234567"), new BigDecimal("2.3456")));

        assertEquals(new BigDecimal("2.8958"), response.frozenAmount());
    }

    @Test
    void buyOrderRejectsInsufficientCashWithoutCreatingOrder() {
        stubCommonBuySellState(account(new BigDecimal("100.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")), riskConfig());

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
        verify(simulationAccountMapper, never()).updateById(any(SimulationAccount.class));
    }

    @Test
    void sellOrderSucceedsAndFreezesPositionQuantity() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")), riskConfig());
        when(portfolioPositionMapper.selectOne(any())).thenReturn(position(new BigDecimal("500.000000")));
        when(portfolioPositionMapper.updateById(any(PortfolioPosition.class))).thenReturn(1);
        when(simulatedOrderMapper.insert(any(SimulatedOrder.class))).thenAnswer(invocation -> {
            SimulatedOrder order = invocation.getArgument(0);
            order.setId(101L);
            return 1;
        });

        SimulatedOrderResponse response = simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.SELL, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000")));

        assertEquals(OrderStatus.OPEN, response.status());
        assertEquals(new BigDecimal("200.000000"), response.frozenQuantity());

        ArgumentCaptor<PortfolioPosition> positionCaptor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(portfolioPositionMapper).updateById(positionCaptor.capture());
        assertEquals(new BigDecimal("300.000000"), positionCaptor.getValue().getAvailableQuantity());
        assertEquals(new BigDecimal("200.000000"), positionCaptor.getValue().getFrozenQuantity());
        verify(cashLedgerMapper, never()).insert(any(CashLedger.class));
    }

    @Test
    void buyOrderRejectsWhenOrderInsertFailsAfterCashFreeze() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")), riskConfig());
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedOrderMapper.insert(any(SimulatedOrder.class))).thenReturn(0);

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));
    }

    @Test
    void buyOrderRejectsWhenFreezeLedgerInsertFails() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")), riskConfig());
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedOrderMapper.insert(any(SimulatedOrder.class))).thenAnswer(invocation -> {
            SimulatedOrder order = invocation.getArgument(0);
            order.setId(104L);
            return 1;
        });
        when(cashLedgerMapper.insert(any(CashLedger.class))).thenReturn(0);

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));
    }

    @Test
    void buyOrderDuplicateFreezeLedgerIsConvertedToBusinessException() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")), riskConfig());
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedOrderMapper.insert(any(SimulatedOrder.class))).thenAnswer(invocation -> {
            SimulatedOrder order = invocation.getArgument(0);
            order.setId(105L);
            return 1;
        });
        when(cashLedgerMapper.insert(any(CashLedger.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));
    }

    @Test
    void sellOrderRejectsWhenOrderInsertFailsAfterPositionFreeze() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")), riskConfig());
        when(portfolioPositionMapper.selectOne(any())).thenReturn(position(new BigDecimal("500.000000")));
        when(portfolioPositionMapper.updateById(any(PortfolioPosition.class))).thenReturn(1);
        when(simulatedOrderMapper.insert(any(SimulatedOrder.class))).thenReturn(0);

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.SELL, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));
    }

    @Test
    void sellOrderRejectsInsufficientPositionWithoutCreatingOrder() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")), riskConfig());
        when(portfolioPositionMapper.selectOne(any())).thenReturn(position(new BigDecimal("100.000000")));

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.SELL, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void orderRejectsPortfolioThatDoesNotBelongToAccount() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account(new BigDecimal("100000.0000")));
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(portfolio(2L, 1L));

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verifyNoInteractions(securityInstrumentMapper);
        verifyNoInteractions(portfolioRiskConfigMapper);
        verifyNoInteractions(portfolioPositionMapper);
        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
        verify(simulationAccountMapper, never()).updateById(any(SimulationAccount.class));
    }

    @Test
    void orderRejectsAmountAboveRiskLimit() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")),
                riskConfig(new BigDecimal("1000.0000"), true, true));

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));
    }

    @Test
    void orderRejectsQuantityThatDoesNotMatchLotSize() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")), riskConfig());

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("150.000000"), new BigDecimal("10.0000"))));
    }

    @Test
    void orderRejectsMissingAccount() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(null);

        assertThrows(com.vanyiung.quantguard.exception.ResourceNotFoundException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verifyNoInteractions(investmentPortfolioMapper);
        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void orderRejectsMissingPortfolio() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account(new BigDecimal("100000.0000")));
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(null);

        assertThrows(com.vanyiung.quantguard.exception.ResourceNotFoundException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verifyNoInteractions(securityInstrumentMapper);
        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void orderRejectsMissingInstrument() {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account(new BigDecimal("100000.0000")));
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(portfolio(1L, 1L));
        when(securityInstrumentMapper.selectById(1L)).thenReturn(null);

        assertThrows(com.vanyiung.quantguard.exception.ResourceNotFoundException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verifyNoInteractions(portfolioRiskConfigMapper);
        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void buyOrderRejectsDisabledAccount() {
        SimulationAccount account = account(new BigDecimal("100000.0000"));
        account.setStatus(AccountStatus.DISABLED);
        when(simulationAccountMapper.selectById(1L)).thenReturn(account);

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verifyNoInteractions(investmentPortfolioMapper);
        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void buyOrderRejectsDisabledPortfolio() {
        InvestmentPortfolio portfolio = portfolio(1L, 1L);
        portfolio.setStatus(AccountStatus.DISABLED);
        when(simulationAccountMapper.selectById(1L)).thenReturn(account(new BigDecimal("100000.0000")));
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(portfolio);

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verifyNoInteractions(securityInstrumentMapper);
        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void buyOrderRejectsPortfolioTypeMismatch() {
        InvestmentPortfolio portfolio = portfolio(1L, 1L);
        portfolio.setPortfolioType(AccountType.SHORT_TERM);
        when(simulationAccountMapper.selectById(1L)).thenReturn(account(new BigDecimal("100000.0000")));
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(portfolio);

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verifyNoInteractions(securityInstrumentMapper);
        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void buyOrderRejectsDisabledInstrument() {
        SecurityInstrument instrument = instrument(new BigDecimal("100.000000"));
        instrument.setStatus(InstrumentStatus.DISABLED);
        when(simulationAccountMapper.selectById(1L)).thenReturn(account(new BigDecimal("100000.0000")));
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(portfolio(1L, 1L));
        when(securityInstrumentMapper.selectById(1L)).thenReturn(instrument);

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verifyNoInteractions(portfolioRiskConfigMapper);
        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void buyOrderRejectsWhenBuyIsDisabled() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")),
                riskConfig(new BigDecimal("50000.0000"), false, true));

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verify(simulationAccountMapper, never()).updateById(any(SimulationAccount.class));
        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void sellOrderRejectsWhenSellIsDisabled() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")),
                riskConfig(new BigDecimal("50000.0000"), true, false));

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.SELL, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verifyNoInteractions(portfolioPositionMapper);
        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void sellOrderRejectsMissingPositionWithoutCreatingOrder() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")), riskConfig());
        when(portfolioPositionMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.SELL, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verify(portfolioPositionMapper, never()).updateById(any(PortfolioPosition.class));
        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void buyOrderRejectsWhenOptimisticLockUpdateFails() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")), riskConfig());
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(0);

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.BUY, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void sellOrderRejectsWhenOptimisticLockUpdateFails() {
        stubCommonBuySellState(account(new BigDecimal("100000.0000")), portfolio(1L, 1L), instrument(new BigDecimal("100.000000")), riskConfig());
        when(portfolioPositionMapper.selectOne(any())).thenReturn(position(new BigDecimal("500.000000")));
        when(portfolioPositionMapper.updateById(any(PortfolioPosition.class))).thenReturn(0);

        assertThrows(BusinessRuleException.class, () -> simulatedOrderService.create(new CreateSimulatedOrderRequest(
                1L, 1L, 1L, OrderSide.SELL, OrderType.LIMIT, new BigDecimal("200.000000"), new BigDecimal("10.0000"))));

        verify(simulatedOrderMapper, never()).insert(any(SimulatedOrder.class));
    }

    @Test
    void getByIdRejectsMissingOrder() {
        when(simulatedOrderMapper.selectById(999L)).thenReturn(null);

        assertThrows(com.vanyiung.quantguard.exception.ResourceNotFoundException.class, () -> simulatedOrderService.getById(999L));
    }

    @Test
    void validPortfolioWithoutOrdersReturnsEmptyList() {
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(portfolio(1L, 1L));
        when(simulatedOrderMapper.selectList(any())).thenReturn(List.of());

        assertEquals(List.of(), simulatedOrderService.listByPortfolioId(1L));
    }

    private void stubCommonBuySellState(SimulationAccount account,
                                        InvestmentPortfolio portfolio,
                                        SecurityInstrument instrument,
                                        PortfolioRiskConfig riskConfig) {
        when(simulationAccountMapper.selectById(1L)).thenReturn(account);
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(portfolio);
        when(securityInstrumentMapper.selectById(1L)).thenReturn(instrument);
        when(portfolioRiskConfigMapper.selectOne(any())).thenReturn(riskConfig);
    }

    private SimulationAccount account(BigDecimal availableCash) {
        SimulationAccount account = new SimulationAccount();
        account.setId(1L);
        account.setAccountType(AccountType.LONG_TERM);
        account.setAvailableCash(availableCash);
        account.setFrozenCash(BigDecimal.ZERO);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }

    private InvestmentPortfolio portfolio(Long accountId, Long id) {
        InvestmentPortfolio portfolio = new InvestmentPortfolio();
        portfolio.setId(id);
        portfolio.setAccountId(accountId);
        portfolio.setPortfolioType(AccountType.LONG_TERM);
        portfolio.setStatus(AccountStatus.ACTIVE);
        return portfolio;
    }

    private SecurityInstrument instrument(BigDecimal lotSize) {
        SecurityInstrument instrument = new SecurityInstrument();
        instrument.setId(1L);
        instrument.setExchange(Exchange.SSE);
        instrument.setSymbol("510300");
        instrument.setAssetType(AssetType.ETF);
        instrument.setLotSize(lotSize);
        instrument.setStatus(InstrumentStatus.ACTIVE);
        return instrument;
    }

    private PortfolioRiskConfig riskConfig() {
        return riskConfig(new BigDecimal("50000.0000"), true, true);
    }

    private PortfolioRiskConfig riskConfig(BigDecimal maxSingleOrderAmount, boolean allowBuy, boolean allowSell) {
        PortfolioRiskConfig riskConfig = new PortfolioRiskConfig();
        riskConfig.setPortfolioId(1L);
        riskConfig.setMaxSingleOrderAmount(maxSingleOrderAmount);
        riskConfig.setMaxPositionRatio(new BigDecimal("0.500000"));
        riskConfig.setAllowBuy(allowBuy);
        riskConfig.setAllowSell(allowSell);
        riskConfig.setStatus(RiskConfigStatus.ACTIVE);
        return riskConfig;
    }

    private PortfolioPosition position(BigDecimal availableQuantity) {
        PortfolioPosition position = new PortfolioPosition();
        position.setId(1L);
        position.setPortfolioId(1L);
        position.setInstrumentId(1L);
        position.setTotalQuantity(availableQuantity);
        position.setAvailableQuantity(availableQuantity);
        position.setFrozenQuantity(BigDecimal.ZERO);
        position.setAverageCost(new BigDecimal("5.0000"));
        return position;
    }
}
