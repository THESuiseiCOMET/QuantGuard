import axios from 'axios'
import { http } from './http'
import type { ApiResponse } from '@/types/api'
import type {
  CreatePortfolioRiskConfigRequest,
  PortfolioRiskConfig,
  UpdatePortfolioRiskConfigRequest,
} from '@/types/risk'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

export function getRiskConfigByPortfolioId(portfolioId: number) {
  return axios
    .get<ApiResponse<PortfolioRiskConfig>>(`/portfolios/${portfolioId}/risk-config`, {
      baseURL,
      timeout: 10_000,
    })
    .then((response) => response.data.data)
}

export function createRiskConfig(payload: CreatePortfolioRiskConfigRequest) {
  return http.post('/risk-configs', payload) as Promise<PortfolioRiskConfig>
}

export function updateRiskConfig(id: number, payload: UpdatePortfolioRiskConfigRequest) {
  return http.put(`/risk-configs/${id}`, payload) as Promise<PortfolioRiskConfig>
}
