export type OrderSide = 'BUY' | 'SELL'
export type OrderType = 'LIMIT'
export type OrderStatus = 'OPEN' | 'REJECTED' | 'FILLED' | 'CANCELED'
export type DecimalValue = string | number

export interface SimulatedOrder {
  id: number
  orderNo: string
  accountId: number
  portfolioId: number
  instrumentId: number
  side: OrderSide
  orderType: OrderType
  requestedQuantity: DecimalValue
  limitPrice: DecimalValue
  frozenAmount: DecimalValue
  frozenQuantity: DecimalValue
  status: OrderStatus
  rejectionReason: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateSimulatedOrderRequest {
  accountId: number
  portfolioId: number
  instrumentId: number
  side: OrderSide
  orderType: OrderType
  requestedQuantity: string
  limitPrice: string
}
