package com.vanyiung.quantguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vanyiung.quantguard.enums.AssetType;
import com.vanyiung.quantguard.enums.Exchange;
import com.vanyiung.quantguard.enums.InstrumentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("security_instrument")
public class SecurityInstrument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String symbol;

    private Exchange exchange;

    private String instrumentName;

    private AssetType assetType;

    private String currency;

    private BigDecimal lotSize;

    private InstrumentStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
