package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CreateInvestmentPortfolioRequest;
import com.vanyiung.quantguard.dto.InvestmentPortfolioResponse;

import java.util.List;

public interface InvestmentPortfolioService {

    InvestmentPortfolioResponse create(CreateInvestmentPortfolioRequest request);

    List<InvestmentPortfolioResponse> listAll();

    InvestmentPortfolioResponse getById(Long id);

    List<InvestmentPortfolioResponse> listByAccountId(Long accountId);
}
