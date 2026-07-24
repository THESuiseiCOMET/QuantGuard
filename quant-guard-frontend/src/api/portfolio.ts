import { http } from './http'
import type { CreateInvestmentPortfolioRequest, InvestmentPortfolio } from '@/types/portfolio'

export function listPortfolios() {
  return http.get('/portfolios') as Promise<InvestmentPortfolio[]>
}

export function listPortfoliosByAccount(accountId: number) {
  return http.get(`/accounts/${accountId}/portfolios`) as Promise<InvestmentPortfolio[]>
}

export function createPortfolio(payload: CreateInvestmentPortfolioRequest) {
  return http.post('/portfolios', payload) as Promise<InvestmentPortfolio>
}
