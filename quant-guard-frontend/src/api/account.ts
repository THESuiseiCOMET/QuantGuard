import { http } from './http'
import type { CreateSimulationAccountRequest, SimulationAccount } from '@/types/account'

export function listAccounts() {
  return http.get('/accounts') as Promise<SimulationAccount[]>
}

export function getAccount(id: number) {
  return http.get(`/accounts/${id}`) as Promise<SimulationAccount>
}

export function createAccount(payload: CreateSimulationAccountRequest) {
  return http.post('/accounts', payload) as Promise<SimulationAccount>
}
