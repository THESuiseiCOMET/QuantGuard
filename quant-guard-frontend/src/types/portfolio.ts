import type { AccountStatus, AccountType } from './account'

export interface InvestmentPortfolio {
  id: number
  accountId: number
  portfolioName: string
  portfolioType: AccountType
  description: string | null
  status: AccountStatus
  createdAt: string
  updatedAt: string
}

export interface CreateInvestmentPortfolioRequest {
  accountId: number
  portfolioName: string
  portfolioType: AccountType
  description?: string
}
