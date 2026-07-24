import { http } from './http'
import type { CashLedgerEntry } from '@/types/cashLedger'

export function listCashLedgerByAccountId(accountId: number) {
  return http.get(`/accounts/${accountId}/cash-ledger`) as Promise<CashLedgerEntry[]>
}
