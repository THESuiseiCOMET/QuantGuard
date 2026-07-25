import { http } from './http'
import type {
  AccountStatus,
  CreateSimulationAccountRequest,
  SimulationAccount,
} from '@/types/account'

export function listAccounts() {
  return http.get('/accounts') as Promise<SimulationAccount[]>
}

export function getAccount(id: number) {
  return http.get(`/accounts/${id}`) as Promise<SimulationAccount>
}

export function createAccount(payload: CreateSimulationAccountRequest) {
  return http.post('/accounts', payload) as Promise<SimulationAccount>
}

export function updateAccountStatus(id: number, status: AccountStatus) {
  return http.patch(`/accounts/${id}/status`, { status }) as Promise<SimulationAccount>
}

export function deleteAccount(id: number) {
  return http.delete(`/accounts/${id}`) as Promise<null>
}

export function forceDeleteAccount(id: number) {
  return http.delete(`/accounts/${id}/force`) as Promise<null>
}
