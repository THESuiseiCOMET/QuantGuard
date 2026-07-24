import { http } from './http'
import type { SecurityInstrument } from '@/types/instrument'

export function listInstruments() {
  return http.get('/instruments') as Promise<SecurityInstrument[]>
}
