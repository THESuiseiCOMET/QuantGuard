package com.chenbo.quantguard.dto;

import com.chenbo.quantguard.entity.InvestmentPortfolio;
import com.chenbo.quantguard.enums.AccountStatus;
import com.chenbo.quantguard.enums.AccountType;

import java.time.LocalDateTime;

public record InvestmentPortfolioResponse(
        Long id,
        Long accountId,
        String portfolioName,
        AccountType portfolioType,
        String description,
        AccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static InvestmentPortfolioResponse from(InvestmentPortfolio portfolio) {
        return new InvestmentPortfolioResponse(
                portfolio.getId(),
                portfolio.getAccountId(),
                portfolio.getPortfolioName(),
                portfolio.getPortfolioType(),
                portfolio.getDescription(),
                portfolio.getStatus(),
                portfolio.getCreatedAt(),
                portfolio.getUpdatedAt()
        );
    }
}
