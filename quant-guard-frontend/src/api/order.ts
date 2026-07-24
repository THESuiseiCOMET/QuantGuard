import { http } from './http'
import type { CreateSimulatedOrderRequest, SimulatedOrder } from '@/types/order'
import type { ExecuteSimulatedOrderResponse } from '@/types/trade'

export function listOrdersByPortfolioId(portfolioId: number) {
  return http.get(`/portfolios/${portfolioId}/orders`) as Promise<SimulatedOrder[]>
}

export function createOrder(payload: CreateSimulatedOrderRequest) {
  return http.post('/orders', payload) as Promise<SimulatedOrder>
}

export function executeOrder(orderId: number) {
  return http.post(`/orders/${orderId}/execute`) as Promise<ExecuteSimulatedOrderResponse>
}

export function cancelOrder(orderId: number) {
  return http.post(`/orders/${orderId}/cancel`) as Promise<SimulatedOrder>
}
