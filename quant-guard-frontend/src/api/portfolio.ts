import { http } from './http'
import type { AccountStatus } from '@/types/account'
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

export function updatePortfolioStatus(id: number, status: AccountStatus) {
  return http.patch(`/portfolios/${id}/status`, { status }) as Promise<InvestmentPortfolio>
}

export function deletePortfolio(id: number) {
  return http.delete(`/portfolios/${id}`) as Promise<null>
}
