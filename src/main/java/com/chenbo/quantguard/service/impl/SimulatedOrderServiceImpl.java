package com.chenbo.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chenbo.quantguard.dto.CreateSimulatedOrderRequest;
import com.chenbo.quantguard.dto.SimulatedOrderResponse;
import com.chenbo.quantguard.entity.InvestmentPortfolio;
import com.chenbo.quantguard.entity.PortfolioPosition;
import com.chenbo.quantguard.entity.PortfolioRiskConfig;
import com.chenbo.quantguard.entity.SecurityInstrument;
import com.chenbo.quantguard.entity.SimulatedOrder;
import com.chenbo.quantguard.entity.SimulationAccount;
import com.chenbo.quantguard.enums.AccountStatus;
import com.chenbo.quantguard.enums.InstrumentStatus;
import com.chenbo.quantguard.enums.OrderSide;
import com.chenbo.quantguard.enums.OrderStatus;
import com.chenbo.quantguard.enums.OrderType;
import com.chenbo.quantguard.enums.RiskConfigStatus;
import com.chenbo.quantguard.exception.BusinessRuleException;
import com.chenbo.quantguard.exception.ResourceNotFoundException;
import com.chenbo.quantguard.mapper.InvestmentPortfolioMapper;
import com.chenbo.quantguard.mapper.PortfolioPositionMapper;
import com.chenbo.quantguard.mapper.PortfolioRiskConfigMapper;
import com.chenbo.quantguard.mapper.SecurityInstrumentMapper;
import com.chenbo.quantguard.mapper.SimulatedOrderMapper;
import com.chenbo.quantguard.mapper.SimulationAccountMapper;
import com.chenbo.quantguard.service.SimulatedOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class SimulatedOrderServiceImpl implements SimulatedOrderService {

    private static final int MONEY_SCALE = 4;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final SimulatedOrderMapper simulatedOrderMapper;
    private final SimulationAccountMapper simulationAccountMapper;
    private final InvestmentPortfolioMapper investmentPortfolioMapper;
    private final SecurityInstrumentMapper securityInstrumentMapper;
    private final PortfolioRiskConfigMapper portfolioRiskConfigMapper;
    private final PortfolioPositionMapper portfolioPositionMapper;

    public SimulatedOrderServiceImpl(SimulatedOrderMapper simulatedOrderMapper,
                                     SimulationAccountMapper simulationAccountMapper,
                                     InvestmentPortfolioMapper investmentPortfolioMapper,
                                     SecurityInstrumentMapper securityInstrumentMapper,
                                     PortfolioRiskConfigMapper portfolioRiskConfigMapper,
                                     PortfolioPositionMapper portfolioPositionMapper) {
        this.simulatedOrderMapper = simulatedOrderMapper;
        this.simulationAccountMapper = simulationAccountMapper;
        this.investmentPortfolioMapper = investmentPortfolioMapper;
        this.securityInstrumentMapper = securityInstrumentMapper;
        this.portfolioRiskConfigMapper = portfolioRiskConfigMapper;
        this.portfolioPositionMapper = portfolioPositionMapper;
    }

    @Override
    @Transactional
    public SimulatedOrderResponse create(CreateSimulatedOrderRequest request) {
        if (request.orderType() != OrderType.LIMIT) {
            throw new BusinessRuleException("Only LIMIT orders are supported");
        }

        SimulationAccount account = requireActiveAccount(request.accountId());
        InvestmentPortfolio portfolio = requireActivePortfolio(request.portfolioId());
        validateAccountAndPortfolio(account, portfolio);
        SecurityInstrument instrument = requireActiveInstrument(request.instrumentId());
        PortfolioRiskConfig riskConfig = requireActiveRiskConfig(request.portfolioId());
        validateLotSize(request.requestedQuantity(), instrument.getLotSize());

        BigDecimal orderAmount = request.requestedQuantity()
                .multiply(request.limitPrice())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (orderAmount.compareTo(riskConfig.getMaxSingleOrderAmount()) > 0) {
            throw new BusinessRuleException("Order amount exceeds max single order amount");
        }

        LocalDateTime now = LocalDateTime.now();
        SimulatedOrder order = new SimulatedOrder();
        order.setOrderNo(newOrderNo(now));
        order.setAccountId(request.accountId());
        order.setPortfolioId(request.portfolioId());
        order.setInstrumentId(request.instrumentId());
        order.setSide(request.side());
        order.setOrderType(request.orderType());
        order.setRequestedQuantity(request.requestedQuantity());
        order.setLimitPrice(request.limitPrice());
        order.setStatus(OrderStatus.OPEN);
        order.setRejectionReason(null);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setVersion(0);

        if (request.side() == OrderSide.BUY) {
            freezeCash(account, riskConfig, orderAmount);
            order.setFrozenAmount(orderAmount);
            order.setFrozenQuantity(ZERO);
        } else {
            freezePosition(request, riskConfig);
            order.setFrozenAmount(ZERO);
            order.setFrozenQuantity(request.requestedQuantity());
        }

        simulatedOrderMapper.insert(order);
        return SimulatedOrderResponse.from(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimulatedOrderResponse> listAll() {
        return simulatedOrderMapper.selectList(Wrappers.<SimulatedOrder>lambdaQuery()
                        .orderByDesc(SimulatedOrder::getId))
                .stream()
                .map(SimulatedOrderResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SimulatedOrderResponse getById(Long id) {
        SimulatedOrder order = simulatedOrderMapper.selectById(id);
        if (order == null) {
            throw new ResourceNotFoundException("Simulated order not found");
        }
        return SimulatedOrderResponse.from(order);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SimulatedOrderResponse> listByPortfolioId(Long portfolioId) {
        InvestmentPortfolio portfolio = investmentPortfolioMapper.selectById(portfolioId);
        if (portfolio == null) {
            throw new ResourceNotFoundException("Investment portfolio not found");
        }

        return simulatedOrderMapper.selectList(Wrappers.<SimulatedOrder>lambdaQuery()
                        .eq(SimulatedOrder::getPortfolioId, portfolioId)
                        .orderByDesc(SimulatedOrder::getId))
                .stream()
                .map(SimulatedOrderResponse::from)
                .toList();
    }

    private SimulationAccount requireActiveAccount(Long accountId) {
        SimulationAccount account = simulationAccountMapper.selectById(accountId);
        if (account == null) {
            throw new ResourceNotFoundException("Simulation account not found");
        }
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Simulation account is not active");
        }
        return account;
    }

    private InvestmentPortfolio requireActivePortfolio(Long portfolioId) {
        InvestmentPortfolio portfolio = investmentPortfolioMapper.selectById(portfolioId);
        if (portfolio == null) {
            throw new ResourceNotFoundException("Investment portfolio not found");
        }
        if (portfolio.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Investment portfolio is not active");
        }
        return portfolio;
    }

    private void validateAccountAndPortfolio(SimulationAccount account, InvestmentPortfolio portfolio) {
        if (!account.getId().equals(portfolio.getAccountId())) {
            throw new BusinessRuleException("Portfolio does not belong to the simulation account");
        }
        if (account.getAccountType() != portfolio.getPortfolioType()) {
            throw new BusinessRuleException("Portfolio type must match simulation account type");
        }
    }

    private SecurityInstrument requireActiveInstrument(Long instrumentId) {
        SecurityInstrument instrument = securityInstrumentMapper.selectById(instrumentId);
        if (instrument == null) {
            throw new ResourceNotFoundException("Security instrument not found");
        }
        if (instrument.getStatus() != InstrumentStatus.ACTIVE) {
            throw new BusinessRuleException("Security instrument is not active");
        }
        return instrument;
    }

    private PortfolioRiskConfig requireActiveRiskConfig(Long portfolioId) {
        PortfolioRiskConfig riskConfig = portfolioRiskConfigMapper.selectOne(Wrappers.<PortfolioRiskConfig>lambdaQuery()
                .eq(PortfolioRiskConfig::getPortfolioId, portfolioId));
        if (riskConfig == null) {
            throw new ResourceNotFoundException("Portfolio risk config not found");
        }
        if (riskConfig.getStatus() != RiskConfigStatus.ACTIVE) {
            throw new BusinessRuleException("Portfolio risk config is not active");
        }
        return riskConfig;
    }

    private void validateLotSize(BigDecimal quantity, BigDecimal lotSize) {
        if (quantity.remainder(lotSize).compareTo(ZERO) != 0) {
            throw new BusinessRuleException("Requested quantity must be a multiple of lot size");
        }
    }

    private void freezeCash(SimulationAccount account, PortfolioRiskConfig riskConfig, BigDecimal orderAmount) {
        if (!Boolean.TRUE.equals(riskConfig.getAllowBuy())) {
            throw new BusinessRuleException("Buy orders are not allowed for this portfolio");
        }
        if (account.getAvailableCash().compareTo(orderAmount) < 0) {
            throw new BusinessRuleException("Available cash is insufficient");
        }

        account.setAvailableCash(account.getAvailableCash().subtract(orderAmount));
        account.setFrozenCash(account.getFrozenCash().add(orderAmount));
        account.setUpdatedAt(LocalDateTime.now());
        int updatedRows = simulationAccountMapper.updateById(account);
        if (updatedRows != 1) {
            throw new BusinessRuleException("Account cash changed, please retry");
        }
    }

    private void freezePosition(CreateSimulatedOrderRequest request, PortfolioRiskConfig riskConfig) {
        if (!Boolean.TRUE.equals(riskConfig.getAllowSell())) {
            throw new BusinessRuleException("Sell orders are not allowed for this portfolio");
        }

        PortfolioPosition position = portfolioPositionMapper.selectOne(Wrappers.<PortfolioPosition>lambdaQuery()
                .eq(PortfolioPosition::getPortfolioId, request.portfolioId())
                .eq(PortfolioPosition::getInstrumentId, request.instrumentId()));
        if (position == null || position.getAvailableQuantity().compareTo(request.requestedQuantity()) < 0) {
            throw new BusinessRuleException("Available position quantity is insufficient");
        }

        position.setAvailableQuantity(position.getAvailableQuantity().subtract(request.requestedQuantity()));
        position.setFrozenQuantity(position.getFrozenQuantity().add(request.requestedQuantity()));
        position.setUpdatedAt(LocalDateTime.now());
        int updatedRows = portfolioPositionMapper.updateById(position);
        if (updatedRows != 1) {
            throw new BusinessRuleException("Position quantity changed, please retry");
        }
    }

    private String newOrderNo(LocalDateTime now) {
        return "SO" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
