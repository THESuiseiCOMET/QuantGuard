package com.vanyiung.quantguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vanyiung.quantguard.enums.AdjustmentType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("instrument_daily_price")
public class InstrumentDailyPrice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long instrumentId;

    private LocalDate tradeDate;

    private BigDecimal openPrice;

    private BigDecimal highPrice;

    private BigDecimal lowPrice;

    private BigDecimal closePrice;

    private BigDecimal previousClose;

    private BigDecimal priceChange;

    private BigDecimal percentageChange;

    private BigDecimal volumeLots;

    private BigDecimal amountCny;

    private AdjustmentType adjustmentType;

    private String dataSource;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
