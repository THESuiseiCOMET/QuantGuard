package com.vanyiung.quantguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vanyiung.quantguard.enums.OrderSide;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("simulated_trade")
public class SimulatedTrade {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tradeNo;

    private Long orderId;

    private Long accountId;

    private Long portfolioId;

    private Long instrumentId;

    private OrderSide side;

    private BigDecimal quantity;

    private BigDecimal executionPrice;

    private BigDecimal tradeAmount;

    private LocalDateTime executedAt;

    private LocalDateTime createdAt;
}
