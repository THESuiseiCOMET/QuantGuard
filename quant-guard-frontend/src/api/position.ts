import { http } from './http'
import type { PortfolioPosition } from '@/types/position'

export function listPositionsByPortfolio(portfolioId: number) {
  return http.get(`/portfolios/${portfolioId}/positions`) as Promise<PortfolioPosition[]>
}
