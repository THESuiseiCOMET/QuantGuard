package com.chenbo.quantguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chenbo.quantguard.enums.OrderSide;
import com.chenbo.quantguard.enums.OrderStatus;
import com.chenbo.quantguard.enums.OrderType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("simulated_order")
public class SimulatedOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long accountId;

    private Long portfolioId;

    private Long instrumentId;

    private OrderSide side;

    private OrderType orderType;

    private BigDecimal requestedQuantity;

    private BigDecimal limitPrice;

    private BigDecimal frozenAmount;

    private BigDecimal frozenQuantity;

    private OrderStatus status;

    private String rejectionReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
