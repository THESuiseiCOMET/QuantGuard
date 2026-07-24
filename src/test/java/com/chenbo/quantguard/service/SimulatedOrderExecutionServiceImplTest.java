package com.chenbo.quantguard.service;

import com.chenbo.quantguard.dto.ExecuteSimulatedOrderResponse;
import com.chenbo.quantguard.dto.SimulatedOrderResponse;
import com.chenbo.quantguard.entity.CashLedger;
import com.chenbo.quantguard.entity.InvestmentPortfolio;
import com.chenbo.quantguard.entity.PortfolioPosition;
import com.chenbo.quantguard.entity.SecurityInstrument;
import com.chenbo.quantguard.entity.SimulatedOrder;
import com.chenbo.quantguard.entity.SimulatedTrade;
import com.chenbo.quantguard.entity.SimulationAccount;
import com.chenbo.quantguard.enums.AccountStatus;
import com.chenbo.quantguard.enums.AccountType;
import com.chenbo.quantguard.enums.CashLedgerEntryType;
import com.chenbo.quantguard.enums.OrderSide;
import com.chenbo.quantguard.enums.OrderStatus;
import com.chenbo.quantguard.enums.OrderType;
import com.chenbo.quantguard.exception.BusinessRuleException;
import com.chenbo.quantguard.mapper.CashLedgerMapper;
import com.chenbo.quantguard.mapper.InvestmentPortfolioMapper;
import com.chenbo.quantguard.mapper.PortfolioPositionMapper;
import com.chenbo.quantguard.mapper.SecurityInstrumentMapper;
import com.chenbo.quantguard.mapper.SimulatedOrderMapper;
import com.chenbo.quantguard.mapper.SimulatedTradeMapper;
import com.chenbo.quantguard.mapper.SimulationAccountMapper;
import com.chenbo.quantguard.service.impl.SimulatedOrderExecutionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulatedOrderExecutionServiceImplTest {

    @Mock
    private SimulatedOrderMapper simulatedOrderMapper;

    @Mock
    private SimulationAccountMapper simulationAccountMapper;

    @Mock
    private InvestmentPortfolioMapper investmentPortfolioMapper;

    @Mock
    private SecurityInstrumentMapper securityInstrumentMapper;

    @Mock
    private PortfolioPositionMapper portfolioPositionMapper;

    @Mock
    private SimulatedTradeMapper simulatedTradeMapper;

    @Mock
    private CashLedgerMapper cashLedgerMapper;

    private SimulatedOrderExecutionServiceImpl executionService;

    @BeforeEach
    void setUp() {
        executionService = new SimulatedOrderExecutionServiceImpl(
                simulatedOrderMapper,
                simulationAccountMapper,
                investmentPortfolioMapper,
                securityInstrumentMapper,
                portfolioPositionMapper,
                simulatedTradeMapper,
                cashLedgerMapper);
    }

    @Test
    void openBuyOrderExecutesAndCreatesPositionTradeAndLedger() {
        SimulatedOrder order = buyOrder();
        stubOrderAndRelations(order, account(new BigDecimal("80000.0000"), new BigDecimal("2000.0000")));
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(portfolioPositionMapper.selectOne(any())).thenReturn(null);
        when(portfolioPositionMapper.insert(any(PortfolioPosition.class))).thenReturn(1);
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedTradeMapper.insert(any(SimulatedTrade.class))).thenAnswer(invocation -> {
            SimulatedTrade trade = invocation.getArgument(0);
            trade.setId(200L);
            return 1;
        });
        when(cashLedgerMapper.insert(any(CashLedger.class))).thenReturn(1);

        ExecuteSimulatedOrderResponse response = executionService.execute(100L);

        assertEquals(OrderStatus.FILLED, response.order().status());
        assertEquals(new BigDecimal("2000.0000"), response.trade().tradeAmount());

        ArgumentCaptor<SimulationAccount> accountCaptor = ArgumentCaptor.forClass(SimulationAccount.class);
        verify(simulationAccountMapper).updateById(accountCaptor.capture());
        assertEquals(new BigDecimal("80000.0000"), accountCaptor.getValue().getAvailableCash());
        assertEquals(new BigDecimal("0.0000"), accountCaptor.getValue().getFrozenCash());

        ArgumentCaptor<PortfolioPosition> positionCaptor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(portfolioPositionMapper).insert(positionCaptor.capture());
        assertEquals(new BigDecimal("200.000000"), positionCaptor.getValue().getTotalQuantity());
        assertEquals(new BigDecimal("10.0000"), positionCaptor.getValue().getAverageCost());

        ArgumentCaptor<CashLedger> ledgerCaptor = ArgumentCaptor.forClass(CashLedger.class);
        verify(cashLedgerMapper).insert(ledgerCaptor.capture());
        assertEquals(CashLedgerEntryType.BUY_SETTLEMENT, ledgerCaptor.getValue().getEntryType());
        assertEquals(new BigDecimal("-2000.0000"), ledgerCaptor.getValue().getFrozenCashDelta());
        verify(cashLedgerMapper, times(1)).insert(any(CashLedger.class));
    }

    @Test
    void openBuyOrderAddsExistingPositionAndWeightedAverageCost() {
        SimulatedOrder order = buyOrder();
        stubOrderAndRelations(order, account(new BigDecimal("80000.0000"), new BigDecimal("2000.0000")));
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(portfolioPositionMapper.selectOne(any())).thenReturn(position(new BigDecimal("100.000000"),
                new BigDecimal("100.000000"), BigDecimal.ZERO, new BigDecimal("8.0000")));
        when(portfolioPositionMapper.updateById(any(PortfolioPosition.class))).thenReturn(1);
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedTradeMapper.insert(any(SimulatedTrade.class))).thenReturn(1);
        when(cashLedgerMapper.insert(any(CashLedger.class))).thenReturn(1);

        executionService.execute(100L);

        ArgumentCaptor<PortfolioPosition> positionCaptor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(portfolioPositionMapper).updateById(positionCaptor.capture());
        assertEquals(new BigDecimal("300.000000"), positionCaptor.getValue().getTotalQuantity());
        assertEquals(new BigDecimal("9.3333"), positionCaptor.getValue().getAverageCost());
    }

    @Test
    void openSellOrderExecutesAndIncreasesAvailableCash() {
        SimulatedOrder order = sellOrder();
        stubOrderAndRelations(order, account(new BigDecimal("80000.0000"), BigDecimal.ZERO));
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(portfolioPositionMapper.selectOne(any())).thenReturn(position(new BigDecimal("500.000000"),
                new BigDecimal("300.000000"), new BigDecimal("200.000000"), new BigDecimal("5.0000")));
        when(portfolioPositionMapper.updateById(any(PortfolioPosition.class))).thenReturn(1);
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedTradeMapper.insert(any(SimulatedTrade.class))).thenAnswer(invocation -> {
            SimulatedTrade trade = invocation.getArgument(0);
            trade.setId(201L);
            return 1;
        });
        when(cashLedgerMapper.insert(any(CashLedger.class))).thenReturn(1);

        ExecuteSimulatedOrderResponse response = executionService.execute(100L);

        assertEquals(new BigDecimal("2000.0000"), response.trade().tradeAmount());

        ArgumentCaptor<PortfolioPosition> positionCaptor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(portfolioPositionMapper).updateById(positionCaptor.capture());
        assertEquals(new BigDecimal("300.000000"), positionCaptor.getValue().getTotalQuantity());
        assertEquals(new BigDecimal("300.000000"), positionCaptor.getValue().getAvailableQuantity());
        assertEquals(new BigDecimal("0.000000"), positionCaptor.getValue().getFrozenQuantity());

        ArgumentCaptor<SimulationAccount> accountCaptor = ArgumentCaptor.forClass(SimulationAccount.class);
        verify(simulationAccountMapper).updateById(accountCaptor.capture());
        assertEquals(new BigDecimal("82000.0000"), accountCaptor.getValue().getAvailableCash());

        ArgumentCaptor<CashLedger> ledgerCaptor = ArgumentCaptor.forClass(CashLedger.class);
        verify(cashLedgerMapper).insert(ledgerCaptor.capture());
        assertEquals(CashLedgerEntryType.SELL_PROCEEDS, ledgerCaptor.getValue().getEntryType());
        verify(cashLedgerMapper, times(1)).insert(any(CashLedger.class));
    }

    @Test
    void sellExecutionZeroesAverageCostWhenPositionIsClosed() {
        SimulatedOrder order = sellOrder();
        stubOrderAndRelations(order, account(new BigDecimal("80000.0000"), BigDecimal.ZERO));
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(portfolioPositionMapper.selectOne(any())).thenReturn(position(new BigDecimal("200.000000"),
                BigDecimal.ZERO, new BigDecimal("200.000000"), new BigDecimal("5.0000")));
        when(portfolioPositionMapper.updateById(any(PortfolioPosition.class))).thenReturn(1);
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedTradeMapper.insert(any(SimulatedTrade.class))).thenReturn(1);
        when(cashLedgerMapper.insert(any(CashLedger.class))).thenReturn(1);

        executionService.execute(100L);

        ArgumentCaptor<PortfolioPosition> positionCaptor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(portfolioPositionMapper).updateById(positionCaptor.capture());
        assertEquals(new BigDecimal("0.000000"), positionCaptor.getValue().getTotalQuantity());
        assertEquals(new BigDecimal("0.0000"), positionCaptor.getValue().getAverageCost());
    }

    @Test
    void buyCancelReleasesCashAndCreatesLedger() {
        SimulatedOrder order = buyOrder();
        stubOrder(order);
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(simulationAccountMapper.selectById(1L)).thenReturn(account(new BigDecimal("80000.0000"), new BigDecimal("2000.0000")));
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(cashLedgerMapper.insert(any(CashLedger.class))).thenReturn(1);

        SimulatedOrderResponse response = executionService.cancel(100L);

        assertEquals(OrderStatus.CANCELED, response.status());
        ArgumentCaptor<SimulationAccount> accountCaptor = ArgumentCaptor.forClass(SimulationAccount.class);
        verify(simulationAccountMapper).updateById(accountCaptor.capture());
        assertEquals(new BigDecimal("82000.0000"), accountCaptor.getValue().getAvailableCash());
        assertEquals(new BigDecimal("0.0000"), accountCaptor.getValue().getFrozenCash());
        verify(cashLedgerMapper).insert(any(CashLedger.class));
        ArgumentCaptor<CashLedger> ledgerCaptor = ArgumentCaptor.forClass(CashLedger.class);
        verify(cashLedgerMapper).insert(ledgerCaptor.capture());
        assertEquals(CashLedgerEntryType.BUY_CANCEL_RELEASE, ledgerCaptor.getValue().getEntryType());
        verify(cashLedgerMapper, times(1)).insert(any(CashLedger.class));
    }

    @Test
    void sellCancelRestoresAvailablePositionWithoutCashLedger() {
        SimulatedOrder order = sellOrder();
        stubOrder(order);
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(simulationAccountMapper.selectById(1L)).thenReturn(account(new BigDecimal("80000.0000"), BigDecimal.ZERO));
        when(portfolioPositionMapper.selectOne(any())).thenReturn(position(new BigDecimal("500.000000"),
                new BigDecimal("300.000000"), new BigDecimal("200.000000"), new BigDecimal("5.0000")));
        when(portfolioPositionMapper.updateById(any(PortfolioPosition.class))).thenReturn(1);

        executionService.cancel(100L);

        ArgumentCaptor<PortfolioPosition> positionCaptor = ArgumentCaptor.forClass(PortfolioPosition.class);
        verify(portfolioPositionMapper).updateById(positionCaptor.capture());
        assertEquals(new BigDecimal("500.000000"), positionCaptor.getValue().getAvailableQuantity());
        assertEquals(new BigDecimal("0.000000"), positionCaptor.getValue().getFrozenQuantity());
        verify(cashLedgerMapper, never()).insert(any(CashLedger.class));
    }

    @Test
    void filledOrderCannotBeExecutedOrCanceledAgain() {
        SimulatedOrder order = buyOrder();
        order.setStatus(OrderStatus.FILLED);
        stubOrder(order);

        assertThrows(BusinessRuleException.class, () -> executionService.execute(100L));
        assertThrows(BusinessRuleException.class, () -> executionService.cancel(100L));
    }

    @Test
    void canceledOrderCannotBeExecutedOrCanceledAgain() {
        SimulatedOrder order = buyOrder();
        order.setStatus(OrderStatus.CANCELED);
        stubOrder(order);

        assertThrows(BusinessRuleException.class, () -> executionService.execute(100L));
        assertThrows(BusinessRuleException.class, () -> executionService.cancel(100L));
    }

    @Test
    void orderOptimisticLockFailureStopsExecutionBeforeSideEffects() {
        SimulatedOrder order = buyOrder();
        stubOrder(order);
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(0);

        assertThrows(BusinessRuleException.class, () -> executionService.execute(100L));
        verify(simulationAccountMapper, never()).updateById(any(SimulationAccount.class));
        verify(simulatedTradeMapper, never()).insert(any(SimulatedTrade.class));
    }

    @Test
    void accountOptimisticLockFailureThrowsException() {
        SimulatedOrder order = buyOrder();
        stubOrderAndRelations(order, account(new BigDecimal("80000.0000"), new BigDecimal("2000.0000")));
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(0);

        assertThrows(BusinessRuleException.class, () -> executionService.execute(100L));
        verify(simulatedTradeMapper, never()).insert(any(SimulatedTrade.class));
    }

    @Test
    void positionOptimisticLockFailureThrowsException() {
        SimulatedOrder order = sellOrder();
        stubOrderAndRelations(order, account(new BigDecimal("80000.0000"), BigDecimal.ZERO));
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(portfolioPositionMapper.selectOne(any())).thenReturn(position(new BigDecimal("500.000000"),
                new BigDecimal("300.000000"), new BigDecimal("200.000000"), new BigDecimal("5.0000")));
        when(portfolioPositionMapper.updateById(any(PortfolioPosition.class))).thenReturn(0);

        assertThrows(BusinessRuleException.class, () -> executionService.execute(100L));
        verify(simulatedTradeMapper, never()).insert(any(SimulatedTrade.class));
    }

    @Test
    void tradeInsertFailureThrowsException() {
        SimulatedOrder order = buyOrder();
        stubOrderAndRelations(order, account(new BigDecimal("80000.0000"), new BigDecimal("2000.0000")));
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(portfolioPositionMapper.selectOne(any())).thenReturn(null);
        when(portfolioPositionMapper.insert(any(PortfolioPosition.class))).thenReturn(1);
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedTradeMapper.insert(any(SimulatedTrade.class))).thenReturn(0);

        assertThrows(BusinessRuleException.class, () -> executionService.execute(100L));
        verify(cashLedgerMapper, never()).insert(any(CashLedger.class));
    }

    @Test
    void ledgerInsertFailureThrowsException() {
        SimulatedOrder order = buyOrder();
        stubOrderAndRelations(order, account(new BigDecimal("80000.0000"), new BigDecimal("2000.0000")));
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(portfolioPositionMapper.selectOne(any())).thenReturn(null);
        when(portfolioPositionMapper.insert(any(PortfolioPosition.class))).thenReturn(1);
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedTradeMapper.insert(any(SimulatedTrade.class))).thenReturn(1);
        when(cashLedgerMapper.insert(any(CashLedger.class))).thenReturn(0);

        assertThrows(BusinessRuleException.class, () -> executionService.execute(100L));
    }

    @Test
    void duplicateLedgerIsConvertedToBusinessException() {
        SimulatedOrder order = buyOrder();
        stubOrderAndRelations(order, account(new BigDecimal("80000.0000"), new BigDecimal("2000.0000")));
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(portfolioPositionMapper.selectOne(any())).thenReturn(null);
        when(portfolioPositionMapper.insert(any(PortfolioPosition.class))).thenReturn(1);
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedTradeMapper.insert(any(SimulatedTrade.class))).thenReturn(1);
        when(cashLedgerMapper.insert(any(CashLedger.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThrows(BusinessRuleException.class, () -> executionService.execute(100L));
    }

    @Test
    void newPositionInsertFailureThrowsException() {
        SimulatedOrder order = buyOrder();
        stubOrderAndRelations(order, account(new BigDecimal("80000.0000"), new BigDecimal("2000.0000")));
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(portfolioPositionMapper.selectOne(any())).thenReturn(null);
        when(portfolioPositionMapper.insert(any(PortfolioPosition.class))).thenReturn(0);
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);

        assertThrows(BusinessRuleException.class, () -> executionService.execute(100L));
        verify(simulatedTradeMapper, never()).insert(any(SimulatedTrade.class));
    }

    @Test
    void duplicateTradeIsConvertedToBusinessException() {
        SimulatedOrder order = buyOrder();
        stubOrderAndRelations(order, account(new BigDecimal("80000.0000"), new BigDecimal("2000.0000")));
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(portfolioPositionMapper.selectOne(any())).thenReturn(null);
        when(portfolioPositionMapper.insert(any(PortfolioPosition.class))).thenReturn(1);
        when(simulationAccountMapper.updateById(any(SimulationAccount.class))).thenReturn(1);
        when(simulatedTradeMapper.insert(any(SimulatedTrade.class))).thenThrow(new DuplicateKeyException("duplicate"));

        assertThrows(BusinessRuleException.class, () -> executionService.execute(100L));
    }

    @Test
    void sellExecutionDoesNotUseOtherPortfolioPosition() {
        SimulatedOrder order = sellOrder();
        stubOrderAndRelations(order, account(new BigDecimal("80000.0000"), BigDecimal.ZERO));
        when(simulatedOrderMapper.updateById(any(SimulatedOrder.class))).thenReturn(1);
        when(portfolioPositionMapper.selectOne(any())).thenReturn(null);

        assertThrows(BusinessRuleException.class, () -> executionService.execute(100L));
        verify(portfolioPositionMapper, never()).updateById(any(PortfolioPosition.class));
    }

    private void stubOrderAndRelations(SimulatedOrder order, SimulationAccount account) {
        stubOrder(order);
        when(simulationAccountMapper.selectById(1L)).thenReturn(account);
        when(investmentPortfolioMapper.selectById(1L)).thenReturn(portfolio());
        when(securityInstrumentMapper.selectById(1L)).thenReturn(new SecurityInstrument());
    }

    private void stubOrder(SimulatedOrder order) {
        when(simulatedOrderMapper.selectById(100L)).thenReturn(order);
    }

    private SimulatedOrder buyOrder() {
        SimulatedOrder order = baseOrder(OrderSide.BUY);
        order.setFrozenAmount(new BigDecimal("2000.0000"));
        order.setFrozenQuantity(BigDecimal.ZERO.setScale(6));
        return order;
    }

    private SimulatedOrder sellOrder() {
        SimulatedOrder order = baseOrder(OrderSide.SELL);
        order.setFrozenAmount(BigDecimal.ZERO.setScale(4));
        order.setFrozenQuantity(new BigDecimal("200.000000"));
        return order;
    }

    private SimulatedOrder baseOrder(OrderSide side) {
        SimulatedOrder order = new SimulatedOrder();
        order.setId(100L);
        order.setOrderNo("SO20260724000000001");
        order.setAccountId(1L);
        order.setPortfolioId(1L);
        order.setInstrumentId(1L);
        order.setSide(side);
        order.setOrderType(OrderType.LIMIT);
        order.setRequestedQuantity(new BigDecimal("200.000000"));
        order.setLimitPrice(new BigDecimal("10.0000"));
        order.setStatus(OrderStatus.OPEN);
        order.setVersion(0);
        return order;
    }

    private SimulationAccount account(BigDecimal availableCash, BigDecimal frozenCash) {
        SimulationAccount account = new SimulationAccount();
        account.setId(1L);
        account.setAccountType(AccountType.LONG_TERM);
        account.setAvailableCash(availableCash);
        account.setFrozenCash(frozenCash);
        account.setStatus(AccountStatus.ACTIVE);
        account.setVersion(0);
        return account;
    }

    private InvestmentPortfolio portfolio() {
        InvestmentPortfolio portfolio = new InvestmentPortfolio();
        portfolio.setId(1L);
        portfolio.setAccountId(1L);
        portfolio.setPortfolioType(AccountType.LONG_TERM);
        portfolio.setStatus(AccountStatus.ACTIVE);
        return portfolio;
    }

    private PortfolioPosition position(BigDecimal totalQuantity,
                                       BigDecimal availableQuantity,
                                       BigDecimal frozenQuantity,
                                       BigDecimal averageCost) {
        PortfolioPosition position = new PortfolioPosition();
        position.setId(1L);
        position.setPortfolioId(1L);
        position.setInstrumentId(1L);
        position.setTotalQuantity(totalQuantity);
        position.setAvailableQuantity(availableQuantity);
        position.setFrozenQuantity(frozenQuantity);
        position.setAverageCost(averageCost);
        position.setVersion(0);
        return position;
    }
}
