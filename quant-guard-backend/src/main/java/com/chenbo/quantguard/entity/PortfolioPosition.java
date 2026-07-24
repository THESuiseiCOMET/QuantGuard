package com.chenbo.quantguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("portfolio_position")
public class PortfolioPosition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long portfolioId;

    private Long instrumentId;

    private BigDecimal totalQuantity;

    private BigDecimal availableQuantity;

    private BigDecimal frozenQuantity;

    private BigDecimal averageCost;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Integer version;
}
