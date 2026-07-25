package com.vanyiung.quantguard.service;

import com.vanyiung.quantguard.dto.CreateInvestmentPortfolioRequest;
import com.vanyiung.quantguard.dto.InvestmentPortfolioResponse;
import com.vanyiung.quantguard.enums.AccountStatus;

import java.util.List;

public interface InvestmentPortfolioService {

    InvestmentPortfolioResponse create(CreateInvestmentPortfolioRequest request);

    List<InvestmentPortfolioResponse> listAll();

    InvestmentPortfolioResponse getById(Long id);

    List<InvestmentPortfolioResponse> listByAccountId(Long accountId);

    InvestmentPortfolioResponse updateStatus(Long id, AccountStatus status);

    void delete(Long id);
}
