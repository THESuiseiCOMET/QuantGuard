import { http } from './http'
import type { PortfolioPosition, UpdatePortfolioPositionRequest } from '@/types/position'

export function listPositionsByPortfolio(portfolioId: number) {
  return http.get(`/portfolios/${portfolioId}/positions`) as Promise<PortfolioPosition[]>
}

export function deletePosition(positionId: number) {
  return http.delete(`/positions/${positionId}`) as Promise<null>
}

export function updatePosition(positionId: number, payload: UpdatePortfolioPositionRequest) {
  return http.put(`/positions/${positionId}`, payload) as Promise<PortfolioPosition>
}
