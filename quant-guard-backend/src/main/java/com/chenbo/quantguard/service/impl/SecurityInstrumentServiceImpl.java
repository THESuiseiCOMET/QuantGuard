package com.chenbo.quantguard.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chenbo.quantguard.dto.CreateSecurityInstrumentRequest;
import com.chenbo.quantguard.dto.SecurityInstrumentResponse;
import com.chenbo.quantguard.entity.SecurityInstrument;
import com.chenbo.quantguard.enums.InstrumentStatus;
import com.chenbo.quantguard.exception.DuplicateResourceException;
import com.chenbo.quantguard.exception.ResourceNotFoundException;
import com.chenbo.quantguard.mapper.SecurityInstrumentMapper;
import com.chenbo.quantguard.service.SecurityInstrumentService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class SecurityInstrumentServiceImpl implements SecurityInstrumentService {

    private final SecurityInstrumentMapper securityInstrumentMapper;

    public SecurityInstrumentServiceImpl(SecurityInstrumentMapper securityInstrumentMapper) {
        this.securityInstrumentMapper = securityInstrumentMapper;
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
}
