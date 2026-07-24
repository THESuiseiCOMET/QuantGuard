import { http } from './http'
import type { SimulatedTrade } from '@/types/trade'

export function listTradesByPortfolioId(portfolioId: number) {
  return http.get(`/portfolios/${portfolioId}/trades`) as Promise<SimulatedTrade[]>
}

export function listTradesByOrderId(orderId: number) {
  return http.get(`/orders/${orderId}/trades`) as Promise<SimulatedTrade[]>
}

export function getTrade(id: number) {
  return http.get(`/trades/${id}`) as Promise<SimulatedTrade>
}
