import { http } from './http'
import type { InstrumentStatus, SecurityInstrument } from '@/types/instrument'

export function listInstruments() {
  return http.get('/instruments') as Promise<SecurityInstrument[]>
}

export function updateInstrumentStatus(id: number, status: InstrumentStatus) {
  return http.patch(`/instruments/${id}/status`, { status }) as Promise<SecurityInstrument>
}

export function deleteInstrument(id: number) {
  return http.delete(`/instruments/${id}`) as Promise<null>
}

export function forceDeleteInstrument(id: number) {
  return http.delete(`/instruments/${id}/force`) as Promise<null>
}
