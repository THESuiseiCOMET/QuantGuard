import type { DecimalValue, OrderSide, SimulatedOrder } from '@/types/order'

export interface SimulatedTrade {
  id: number
  tradeNo: string
  orderId: number
  accountId: number
  portfolioId: number
  instrumentId: number
  side: OrderSide
  quantity: DecimalValue
  executionPrice: DecimalValue
  tradeAmount: DecimalValue
  executedAt: string
  createdAt: string
}

export interface ExecuteSimulatedOrderResponse {
  order: SimulatedOrder
  trade: SimulatedTrade
}
