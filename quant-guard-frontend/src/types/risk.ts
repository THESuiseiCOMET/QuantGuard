import type { AccountStatus } from './account'

export interface PortfolioRiskConfig {
  id: number
  portfolioId: number
  maxSingleOrderAmount: string
  maxPositionRatio: string
  allowBuy: boolean
  allowSell: boolean
  status: AccountStatus
  createdAt: string
  updatedAt: string
}

export interface CreatePortfolioRiskConfigRequest {
  portfolioId: number
  maxSingleOrderAmount: string
  maxPositionRatio: string
  allowBuy: boolean
  allowSell: boolean
}

export interface UpdatePortfolioRiskConfigRequest {
  maxSingleOrderAmount: string
  maxPositionRatio: string
  allowBuy: boolean
  allowSell: boolean
}
