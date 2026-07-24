package com.vanyiung.quantguard.dto;

import com.vanyiung.quantguard.entity.InstrumentDailyPrice;
import com.vanyiung.quantguard.enums.AdjustmentType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyPriceResponse(
        Long id,
        Long instrumentId,
        LocalDate tradeDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal percentageChange,
        BigDecimal volumeLots,
        BigDecimal amountCny,
        AdjustmentType adjustmentType,
        String dataSource
) {

    public static DailyPriceResponse from(InstrumentDailyPrice price) {
        return new DailyPriceResponse(
                price.getId(),
                price.getInstrumentId(),
                price.getTradeDate(),
                price.getOpenPrice(),
                price.getHighPrice(),
                price.getLowPrice(),
                price.getClosePrice(),
                price.getPreviousClose(),
                price.getPriceChange(),
                price.getPercentageChange(),
                price.getVolumeLots(),
                price.getAmountCny(),
                price.getAdjustmentType(),
                price.getDataSource()
        );
    }
}
