import { http } from './http'
import type { CreateSimulatedOrderRequest, SimulatedOrder } from '@/types/order'

export function listOrdersByPortfolioId(portfolioId: number) {
  return http.get(`/portfolios/${portfolioId}/orders`) as Promise<SimulatedOrder[]>
}

export function createOrder(payload: CreateSimulatedOrderRequest) {
  return http.post('/orders', payload) as Promise<SimulatedOrder>
}
