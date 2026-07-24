package com.chenbo.quantguard.service;

import com.chenbo.quantguard.dto.CreateInvestmentPortfolioRequest;
import com.chenbo.quantguard.dto.InvestmentPortfolioResponse;

import java.util.List;

public interface InvestmentPortfolioService {

    InvestmentPortfolioResponse create(CreateInvestmentPortfolioRequest request);

    List<InvestmentPortfolioResponse> listAll();

    InvestmentPortfolioResponse getById(Long id);

    List<InvestmentPortfolioResponse> listByAccountId(Long accountId);
}
