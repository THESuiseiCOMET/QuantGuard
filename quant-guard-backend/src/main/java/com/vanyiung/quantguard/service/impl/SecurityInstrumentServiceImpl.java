package com.vanyiung.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.vanyiung.quantguard.dto.CreateSecurityInstrumentRequest;
import com.vanyiung.quantguard.dto.SecurityInstrumentResponse;
import com.vanyiung.quantguard.entity.CashLedger;
import com.vanyiung.quantguard.entity.InstrumentDailyPrice;
import com.vanyiung.quantguard.entity.MarketDataSyncLog;
import com.vanyiung.quantguard.entity.PortfolioPosition;
import com.vanyiung.quantguard.entity.SecurityInstrument;
import com.vanyiung.quantguard.entity.SimulatedOrder;
import com.vanyiung.quantguard.entity.SimulatedTrade;
import com.vanyiung.quantguard.enums.InstrumentStatus;
import com.vanyiung.quantguard.enums.MarketDataSyncStatus;
import com.vanyiung.quantguard.enums.OrderStatus;
import com.vanyiung.quantguard.exception.BusinessRuleException;
import com.vanyiung.quantguard.exception.DuplicateResourceException;
import com.vanyiung.quantguard.exception.ResourceNotFoundException;
import com.vanyiung.quantguard.mapper.CashLedgerMapper;
import com.vanyiung.quantguard.mapper.InstrumentDailyPriceMapper;
import com.vanyiung.quantguard.mapper.MarketDataSyncLogMapper;
import com.vanyiung.quantguard.mapper.PortfolioPositionMapper;
import com.vanyiung.quantguard.mapper.SecurityInstrumentMapper;
import com.vanyiung.quantguard.mapper.SimulatedOrderMapper;
import com.vanyiung.quantguard.mapper.SimulatedTradeMapper;
import com.vanyiung.quantguard.service.SecurityInstrumentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class SecurityInstrumentServiceImpl implements SecurityInstrumentService {

    private final SecurityInstrumentMapper securityInstrumentMapper;
    private final PortfolioPositionMapper portfolioPositionMapper;
    private final SimulatedOrderMapper simulatedOrderMapper;
    private final SimulatedTradeMapper simulatedTradeMapper;
    private final CashLedgerMapper cashLedgerMapper;
    private final InstrumentDailyPriceMapper dailyPriceMapper;
    private final MarketDataSyncLogMapper syncLogMapper;

    public SecurityInstrumentServiceImpl(SecurityInstrumentMapper securityInstrumentMapper,
                                         PortfolioPositionMapper portfolioPositionMapper,
                                         SimulatedOrderMapper simulatedOrderMapper,
                                         SimulatedTradeMapper simulatedTradeMapper,
                                         CashLedgerMapper cashLedgerMapper,
                                         InstrumentDailyPriceMapper dailyPriceMapper,
                                         MarketDataSyncLogMapper syncLogMapper) {
        this.securityInstrumentMapper = securityInstrumentMapper;
        this.portfolioPositionMapper = portfolioPositionMapper;
        this.simulatedOrderMapper = simulatedOrderMapper;
        this.simulatedTradeMapper = simulatedTradeMapper;
        this.cashLedgerMapper = cashLedgerMapper;
        this.dailyPriceMapper = dailyPriceMapper;
        this.syncLogMapper = syncLogMapper;
    }

    @Override
    @Transactional
    public SecurityInstrumentResponse create(CreateSecurityInstrumentRequest request) {
        String symbol = request.symbol().trim().toUpperCase(Locale.ROOT);
        String instrumentName = request.instrumentName().trim();
        String currency = request.currency().trim().toUpperCase(Locale.ROOT);

        boolean exists = securityInstrumentMapper.exists(Wrappers.<SecurityInstrument>lambdaQuery()
                .eq(SecurityInstrument::getExchange, request.exchange())
                .eq(SecurityInstrument::getSymbol, symbol));
        if (exists) {
            throw new DuplicateResourceException("Security instrument already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        SecurityInstrument instrument = new SecurityInstrument();
        instrument.setSymbol(symbol);
        instrument.setExchange(request.exchange());
        instrument.setInstrumentName(instrumentName);
        instrument.setAssetType(request.assetType());
        instrument.setCurrency(currency);
        instrument.setLotSize(request.lotSize());
        instrument.setStatus(InstrumentStatus.ACTIVE);
        instrument.setCreatedAt(now);
        instrument.setUpdatedAt(now);

        try {
            securityInstrumentMapper.insert(instrument);
        } catch (DuplicateKeyException ex) {
            throw new DuplicateResourceException("Security instrument already exists");
        }

        return SecurityInstrumentResponse.from(instrument);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SecurityInstrumentResponse> listAll() {
        return securityInstrumentMapper.selectList(Wrappers.<SecurityInstrument>lambdaQuery()
                        .orderByAsc(SecurityInstrument::getExchange)
                        .orderByAsc(SecurityInstrument::getSymbol))
                .stream()
                .map(SecurityInstrumentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SecurityInstrumentResponse getById(Long id) {
        SecurityInstrument instrument = securityInstrumentMapper.selectById(id);
        if (instrument == null) {
            throw new ResourceNotFoundException("Security instrument not found");
        }

        return SecurityInstrumentResponse.from(instrument);
    }

    @Override
    @Transactional
    public SecurityInstrumentResponse updateStatus(Long id, InstrumentStatus status) {
        SecurityInstrument instrument = requireInstrument(id);
        if (instrument.getStatus() == status) {
            return SecurityInstrumentResponse.from(instrument);
        }
        if (status == InstrumentStatus.DISABLED && hasOpenOrders(id)) {
            throw new BusinessRuleException("Security instrument has open orders and cannot be disabled");
        }

        instrument.setStatus(status);
        instrument.setUpdatedAt(LocalDateTime.now());
        int updatedRows = securityInstrumentMapper.updateById(instrument);
        if (updatedRows != 1) {
            throw new BusinessRuleException("Security instrument changed, please retry");
        }
        return SecurityInstrumentResponse.from(instrument);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireInstrument(id);
        if (hasRunningSync(id)) {
            throw new BusinessRuleException("Security instrument has a running market data sync task");
        }
        if (hasTradingData(id)) {
            throw new BusinessRuleException(
                    "Security instrument has related trading data and cannot be deleted. Disable it instead.");
        }

        dailyPriceMapper.delete(Wrappers.<InstrumentDailyPrice>lambdaQuery()
                .eq(InstrumentDailyPrice::getInstrumentId, id));
        syncLogMapper.delete(Wrappers.<MarketDataSyncLog>lambdaQuery()
                .eq(MarketDataSyncLog::getInstrumentId, id));
        int deletedRows = securityInstrumentMapper.deleteById(id);
        if (deletedRows != 1) {
            throw new BusinessRuleException("Security instrument delete failed, please retry");
        }
    }

    private SecurityInstrument requireInstrument(Long id) {
        SecurityInstrument instrument = securityInstrumentMapper.selectById(id);
        if (instrument == null) {
            throw new ResourceNotFoundException("Security instrument not found");
        }
        return instrument;
    }

    private boolean hasOpenOrders(Long instrumentId) {
        return simulatedOrderMapper.exists(Wrappers.<SimulatedOrder>lambdaQuery()
                .eq(SimulatedOrder::getInstrumentId, instrumentId)
                .eq(SimulatedOrder::getStatus, OrderStatus.OPEN));
    }

    private boolean hasRunningSync(Long instrumentId) {
        return syncLogMapper.exists(Wrappers.<MarketDataSyncLog>lambdaQuery()
                .eq(MarketDataSyncLog::getInstrumentId, instrumentId)
                .eq(MarketDataSyncLog::getStatus, MarketDataSyncStatus.RUNNING));
    }

    private boolean hasTradingData(Long instrumentId) {
        return portfolioPositionMapper.exists(Wrappers.<PortfolioPosition>lambdaQuery()
                .eq(PortfolioPosition::getInstrumentId, instrumentId))
                || simulatedOrderMapper.exists(Wrappers.<SimulatedOrder>lambdaQuery()
                .eq(SimulatedOrder::getInstrumentId, instrumentId))
                || simulatedTradeMapper.exists(Wrappers.<SimulatedTrade>lambdaQuery()
                .eq(SimulatedTrade::getInstrumentId, instrumentId))
                || cashLedgerMapper.exists(Wrappers.<CashLedger>query()
                .inSql("order_id", "select id from simulated_order where instrument_id = " + instrumentId));
    }
}
