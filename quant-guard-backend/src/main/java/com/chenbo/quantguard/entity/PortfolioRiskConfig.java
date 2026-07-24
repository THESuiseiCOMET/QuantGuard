package com.chenbo.quantguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chenbo.quantguard.enums.RiskConfigStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("portfolio_risk_config")
public class PortfolioRiskConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long portfolioId;

    private BigDecimal maxSingleOrderAmount;

    private BigDecimal maxPositionRatio;

    private Boolean allowBuy;

    private Boolean allowSell;

    private RiskConfigStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;
}
