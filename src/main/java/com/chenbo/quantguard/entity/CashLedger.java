package com.chenbo.quantguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chenbo.quantguard.enums.CashLedgerEntryType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("cash_ledger")
public class CashLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accountId;

    private Long portfolioId;

    private Long orderId;

    private Long tradeId;

    private CashLedgerEntryType entryType;

    private BigDecimal availableCashDelta;

    private BigDecimal frozenCashDelta;

    private BigDecimal availableCashAfter;

    private BigDecimal frozenCashAfter;

    private String description;

    private LocalDateTime createdAt;
}
