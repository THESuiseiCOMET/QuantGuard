import type { DecimalValue } from '@/types/order'

export type CashLedgerEntryType =
  'BUY_ORDER_FREEZE' | 'BUY_SETTLEMENT' | 'SELL_PROCEEDS' | 'BUY_CANCEL_RELEASE'

export interface CashLedgerEntry {
  id: number
  accountId: number
  portfolioId: number
  orderId: number | null
  tradeId: number | null
  entryType: CashLedgerEntryType
  availableCashDelta: DecimalValue
  frozenCashDelta: DecimalValue
  availableCashAfter: DecimalValue
  frozenCashAfter: DecimalValue
  description: string
  createdAt: string
}
