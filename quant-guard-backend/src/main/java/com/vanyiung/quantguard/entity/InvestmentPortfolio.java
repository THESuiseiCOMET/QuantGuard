package com.vanyiung.quantguard.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.vanyiung.quantguard.enums.AccountStatus;
import com.vanyiung.quantguard.enums.AccountType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("investment_portfolio")
public class InvestmentPortfolio {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accountId;

    private String portfolioName;

    private AccountType portfolioType;

    private String description;

    private AccountStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
