package com.vanyiung.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vanyiung.quantguard.dto.ExecuteSimulatedOrderResponse;
import com.vanyiung.quantguard.dto.SimulatedOrderResponse;
import com.vanyiung.quantguard.dto.SimulatedTradeResponse;
import com.vanyiung.quantguard.entity.CashLedger;
import com.vanyiung.quantguard.entity.InvestmentPortfolio;
import com.vanyiung.quantguard.entity.PortfolioPosition;
import com.vanyiung.quantguard.entity.SecurityInstrument;
import com.vanyiung.quantguard.entity.SimulatedOrder;
import com.vanyiung.quantguard.entity.SimulatedTrade;
import com.vanyiung.quantguard.entity.SimulationAccount;
import com.vanyiung.quantguard.enums.AccountType;
import com.vanyiung.quantguard.enums.CashLedgerEntryType;
import com.vanyiung.quantguard.enums.OrderSide;
import com.vanyiung.quantguard.enums.OrderStatus;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.mapper.CashLedgerMapper;
import com.vanyiung.quantguard.mapper.InvestmentPortfolioMapper;
import com.vanyiung.quantguard.mapper.PortfolioPositionMapper;
import com.vanyiung.quantguard.mapper.SecurityInstrumentMapper;
import com.vanyiung.quantguard.mapper.SimulatedOrderMapper;
import com.vanyiung.quantguard.mapper.SimulatedTradeMapper;
import com.vanyiung.quantguard.mapper.SimulationAccountMapper;
import com.vanyiung.quantguard.service.SimulatedOrderExecutionService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class SimulatedOrderExecutionServiceImpl implements SimulatedOrderExecutionService {

    private static final int MONEY_SCALE = 4;
    private static final int QUANTITY_SCALE = 6;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final SimulatedOrderMapper simulatedOrderMapper;
    private final SimulationAccountMapper simulationAccountMapper;
    private final InvestmentPortfolioMapper investmentPortfolioMapper;
    private final SecurityInstrumentMapper securityInstrumentMapper;
    private final PortfolioPositionMapper portfolioPositionMapper;
    private final SimulatedTradeMapper simulatedTradeMapper;
    private final CashLedgerMapper cashLedgerMapper;

    public SimulatedOrderExecutionServiceImpl(SimulatedOrderMapper simulatedOrderMapper,
                                              SimulationAccountMapper simulationAccountMapper,
                                              InvestmentPortfolioMapper investmentPortfolioMapper,
                                              SecurityInstrumentMapper securityInstrumentMapper,
                                              PortfolioPositionMapper portfolioPositionMapper,
                                              SimulatedTradeMapper simulatedTradeMapper,
                                              CashLedgerMapper cashLedgerMapper) {
        this.simulatedOrderMapper = simulatedOrderMapper;
        this.simulationAccountMapper = simulationAccountMapper;
        this.investmentPortfolioMapper = investmentPortfolioMapper;
        this.securityInstrumentMapper = securityInstrumentMapper;
        this.portfolioPositionMapper = portfolioPositionMapper;
        this.simulatedTradeMapper = simulatedTradeMapper;
        this.cashLedgerMapper = cashLedgerMapper;
    }

    @Override
    @Transactional
    public ExecuteSimulatedOrderResponse execute(Long orderId) {
        SimulatedOrder order = requireOpenOrder(orderId);
        claimOrder(order, OrderStatus.FILLED);

        SimulationAccount account = requireAccount(order.getAccountId());
        InvestmentPortfolio portfolio = requirePortfolio(order.getPortfolioId());
        requireInstrument(order.getInstrumentId());
        validateRelationships(order, account, portfolio);

        SimulatedTrade trade;
        if (order.getSide() == OrderSide.BUY) {
            trade = executeBuy(order, account);
        } else {
            trade = executeSell(order, account);
        }

        return new ExecuteSimulatedOrderResponse(SimulatedOrderResponse.from(order), SimulatedTradeResponse.from(trade));
    }

    @Override
    @Transactional
    public SimulatedOrderResponse cancel(Long orderId) {
        SimulatedOrder order = requireOpenOrder(orderId);
        claimOrder(order, OrderStatus.CANCELED);

        SimulationAccount account = requireAccount(order.getAccountId());
        if (order.getSide() == OrderSide.BUY) {
            cancelBuy(order, account);
        } else {
            cancelSell(order);
        }

        return SimulatedOrderResponse.from(order);
    }

    private SimulatedTrade executeBuy(SimulatedOrder order, SimulationAccount account) {
        BigDecimal tradeAmount = order.getFrozenAmount();
        if (tradeAmount == null || tradeAmount.compareTo(ZERO) <= 0) {
            throw new BusinessRuleException("BUY order frozen amount is invalid");
        }
        if (account.getFrozenCash().compareTo(tradeAmount) < 0) {
            throw new BusinessRuleException("Frozen cash is insufficient");
        }

        account.setFrozenCash(account.getFrozenCash().subtract(tradeAmount));
        account.setUpdatedAt(LocalDateTime.now());
        updateAccount(account);

        upsertBuyPosition(order, tradeAmount);
        SimulatedTrade trade = createTrade(order, tradeAmount);
        createLedger(order, trade.getId(), CashLedgerEntryType.BUY_SETTLEMENT, ZERO, tradeAmount.negate(),
                account.getAvailableCash(), account.getFrozenCash(), "BUY order settlement");

        order.setFrozenAmount(ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        updateOrder(order);
        return trade;
    }

    private SimulatedTrade executeSell(SimulatedOrder order, SimulationAccount account) {
        BigDecimal frozenQuantity = order.getFrozenQuantity();
        if (frozenQuantity == null || frozenQuantity.compareTo(order.getRequestedQuantity()) != 0) {
            throw new BusinessRuleException("SELL order frozen quantity is invalid");
        }

        PortfolioPosition position = findPosition(order);
        if (position == null) {
            throw new BusinessRuleException("Portfolio position not found");
        }
        if (position.getFrozenQuantity().compareTo(frozenQuantity) < 0
                || position.getTotalQuantity().compareTo(order.getRequestedQuantity()) < 0) {
            throw new BusinessRuleException("Frozen position quantity is insufficient");
        }

        position.setTotalQuantity(position.getTotalQuantity().subtract(order.getRequestedQuantity()).setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY));
        position.setFrozenQuantity(position.getFrozenQuantity().subtract(frozenQuantity).setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY));
        if (position.getTotalQuantity().compareTo(ZERO) == 0) {
            position.setAvailableQuantity(ZERO.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY));
            position.setAverageCost(ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        }
        position.setUpdatedAt(LocalDateTime.now());
        updatePosition(position);

        BigDecimal tradeAmount = order.getRequestedQuantity().multiply(order.getLimitPrice()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (tradeAmount.compareTo(ZERO) <= 0) {
            throw new BusinessRuleException("SELL trade amount must be greater than 0");
        }
        account.setAvailableCash(account.getAvailableCash().add(tradeAmount));
        account.setUpdatedAt(LocalDateTime.now());
        updateAccount(account);

        SimulatedTrade trade = createTrade(order, tradeAmount);
        createLedger(order, trade.getId(), CashLedgerEntryType.SELL_PROCEEDS, tradeAmount, ZERO,
                account.getAvailableCash(), account.getFrozenCash(), "SELL order proceeds");

        order.setFrozenQuantity(ZERO.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY));
        updateOrder(order);
        return trade;
    }

    private void cancelBuy(SimulatedOrder order, SimulationAccount account) {
        BigDecimal frozenAmount = order.getFrozenAmount();
        if (frozenAmount == null || frozenAmount.compareTo(ZERO) <= 0) {
            throw new BusinessRuleException("BUY order frozen amount is invalid");
        }
        if (account.getFrozenCash().compareTo(frozenAmount) < 0) {
            throw new BusinessRuleException("Frozen cash is insufficient");
        }

        account.setAvailableCash(account.getAvailableCash().add(frozenAmount));
        account.setFrozenCash(account.getFrozenCash().subtract(frozenAmount));
        account.setUpdatedAt(LocalDateTime.now());
        updateAccount(account);

        createLedger(order, null, CashLedgerEntryType.BUY_CANCEL_RELEASE, frozenAmount, frozenAmount.negate(),
                account.getAvailableCash(), account.getFrozenCash(), "BUY order cancellation release");
        order.setFrozenAmount(ZERO.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        updateOrder(order);
    }

    private void cancelSell(SimulatedOrder order) {
        BigDecimal frozenQuantity = order.getFrozenQuantity();
        if (frozenQuantity == null || frozenQuantity.compareTo(ZERO) <= 0) {
            throw new BusinessRuleException("SELL order frozen quantity is invalid");
        }

        PortfolioPosition position = findPosition(order);
        if (position == null || position.getFrozenQuantity().compareTo(frozenQuantity) < 0) {
            throw new BusinessRuleException("Frozen position quantity is insufficient");
        }

        position.setAvailableQuantity(position.getAvailableQuantity().add(frozenQuantity).setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY));
        position.setFrozenQuantity(position.getFrozenQuantity().subtract(frozenQuantity).setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY));
        position.setUpdatedAt(LocalDateTime.now());
        updatePosition(position);
        order.setFrozenQuantity(ZERO.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY));
        updateOrder(order);
    }

    private void upsertBuyPosition(SimulatedOrder order, BigDecimal tradeAmount) {
        PortfolioPosition position = findPosition(order);
        if (position == null) {
            position = new PortfolioPosition();
            position.setPortfolioId(order.getPortfolioId());
            position.setInstrumentId(order.getInstrumentId());
            position.setTotalQuantity(order.getRequestedQuantity().setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY));
            position.setAvailableQuantity(order.getRequestedQuantity().setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY));
            position.setFrozenQuantity(ZERO.setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY));
            position.setAverageCost(tradeAmount.divide(order.getRequestedQuantity(), MONEY_SCALE, RoundingMode.HALF_UP));
            LocalDateTime now = LocalDateTime.now();
            position.setCreatedAt(now);
            position.setUpdatedAt(now);
            position.setVersion(0);
            try {
                int insertedRows = portfolioPositionMapper.insert(position);
                if (insertedRows != 1) {
                    throw new BusinessRuleException("Portfolio position creation failed, please retry");
                }
            } catch (DuplicateKeyException ex) {
                throw new BusinessRuleException("Portfolio position changed, please retry");
            }
            return;
        }

        BigDecimal oldQuantity = position.getTotalQuantity();
        BigDecimal oldCost = oldQuantity.multiply(position.getAverageCost());
        BigDecimal newQuantity = oldQuantity.add(order.getRequestedQuantity()).setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY);
        BigDecimal newAverageCost = oldCost.add(tradeAmount).divide(newQuantity, MONEY_SCALE, RoundingMode.HALF_UP);
        position.setTotalQuantity(newQuantity);
        position.setAvailableQuantity(position.getAvailableQuantity().add(order.getRequestedQuantity()).setScale(QUANTITY_SCALE, RoundingMode.UNNECESSARY));
        position.setAverageCost(newAverageCost);
        position.setUpdatedAt(LocalDateTime.now());
        updatePosition(position);
    }

    private SimulatedOrder requireOpenOrder(Long orderId) {
        SimulatedOrder order = simulatedOrderMapper.selectById(orderId);
        if (order == null) {
            throw new ResourceNotFoundException("Simulated order not found");
        }
        if (order.getStatus() != OrderStatus.OPEN) {
            throw new BusinessRuleException("Only OPEN orders can be operated");
        }
        return order;
    }

    private void claimOrder(SimulatedOrder order, OrderStatus targetStatus) {
        order.setStatus(targetStatus);
        order.setUpdatedAt(LocalDateTime.now());
        updateOrder(order);
    }

    private void updateOrder(SimulatedOrder order) {
        int updatedRows = simulatedOrderMapper.updateById(order);
        if (updatedRows != 1) {
            throw new BusinessRuleException("Order status changed, please retry");
        }
    }

    private SimulationAccount requireAccount(Long accountId) {
        SimulationAccount account = simulationAccountMapper.selectById(accountId);
        if (account == null) {
            throw new ResourceNotFoundException("Simulation account not found");
        }
        return account;
    }

    private InvestmentPortfolio requirePortfolio(Long portfolioId) {
        InvestmentPortfolio portfolio = investmentPortfolioMapper.selectById(portfolioId);
        if (portfolio == null) {
            throw new ResourceNotFoundException("Investment portfolio not found");
        }
        return portfolio;
    }

    private void requireInstrument(Long instrumentId) {
        SecurityInstrument instrument = securityInstrumentMapper.selectById(instrumentId);
        if (instrument == null) {
            throw new ResourceNotFoundException("Security instrument not found");
        }
    }

    private void validateRelationships(SimulatedOrder order, SimulationAccount account, InvestmentPortfolio portfolio) {
        if (!order.getAccountId().equals(account.getId()) || !order.getPortfolioId().equals(portfolio.getId())) {
            throw new BusinessRuleException("Order ownership is invalid");
        }
        if (!portfolio.getAccountId().equals(account.getId())) {
            throw new BusinessRuleException("Portfolio does not belong to the simulation account");
        }
        AccountType accountType = account.getAccountType();
        if (accountType != portfolio.getPortfolioType()) {
            throw new BusinessRuleException("Portfolio type must match simulation account type");
        }
    }

    private PortfolioPosition findPosition(SimulatedOrder order) {
        return portfolioPositionMapper.selectOne(Wrappers.<PortfolioPosition>lambdaQuery()
                .eq(PortfolioPosition::getPortfolioId, order.getPortfolioId())
                .eq(PortfolioPosition::getInstrumentId, order.getInstrumentId()));
    }

    private void updateAccount(SimulationAccount account) {
        int updatedRows = simulationAccountMapper.updateById(account);
        if (updatedRows != 1) {
            throw new BusinessRuleException("Account cash changed, please retry");
        }
    }

    private void updatePosition(PortfolioPosition position) {
        int updatedRows = portfolioPositionMapper.updateById(position);
        if (updatedRows != 1) {
            throw new BusinessRuleException("Position quantity changed, please retry");
        }
    }

    private SimulatedTrade createTrade(SimulatedOrder order, BigDecimal tradeAmount) {
        LocalDateTime now = LocalDateTime.now();
        SimulatedTrade trade = new SimulatedTrade();
        trade.setTradeNo(newTradeNo(now));
        trade.setOrderId(order.getId());
        trade.setAccountId(order.getAccountId());
        trade.setPortfolioId(order.getPortfolioId());
        trade.setInstrumentId(order.getInstrumentId());
        trade.setSide(order.getSide());
        trade.setQuantity(order.getRequestedQuantity());
        trade.setExecutionPrice(order.getLimitPrice());
        trade.setTradeAmount(tradeAmount);
        trade.setExecutedAt(now);
        trade.setCreatedAt(now);
        try {
            int insertedRows = simulatedTradeMapper.insert(trade);
            if (insertedRows != 1) {
                throw new BusinessRuleException("Simulated trade creation failed");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessRuleException("Order already has a trade, please refresh");
        }
        return trade;
    }

    private void createLedger(SimulatedOrder order,
                              Long tradeId,
                              CashLedgerEntryType entryType,
                              BigDecimal availableCashDelta,
                              BigDecimal frozenCashDelta,
                              BigDecimal availableCashAfter,
                              BigDecimal frozenCashAfter,
                              String description) {
        CashLedger ledger = new CashLedger();
        ledger.setAccountId(order.getAccountId());
        ledger.setPortfolioId(order.getPortfolioId());
        ledger.setOrderId(order.getId());
        ledger.setTradeId(tradeId);
        ledger.setEntryType(entryType);
        ledger.setAvailableCashDelta(availableCashDelta.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        ledger.setFrozenCashDelta(frozenCashDelta.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        ledger.setAvailableCashAfter(availableCashAfter.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        ledger.setFrozenCashAfter(frozenCashAfter.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY));
        ledger.setDescription(description);
        ledger.setCreatedAt(LocalDateTime.now());
        try {
            int insertedRows = cashLedgerMapper.insert(ledger);
            if (insertedRows != 1) {
                throw new BusinessRuleException("Cash ledger creation failed");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessRuleException("Cash ledger entry already exists, please refresh");
        }
    }

    private String newTradeNo(LocalDateTime now) {
        return "ST" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
