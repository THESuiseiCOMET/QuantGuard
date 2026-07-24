export type AccountType = 'LONG_TERM' | 'SHORT_TERM'
export type AccountStatus = 'ACTIVE' | 'DISABLED'

export interface SimulationAccount {
  id: number
  accountName: string
  accountType: AccountType
  initialCash: string
  availableCash: string
  frozenCash: string
  status: AccountStatus
  createdAt: string
  updatedAt: string
}

export interface CreateSimulationAccountRequest {
  accountName: string
  accountType: AccountType
  initialCash: string
}
