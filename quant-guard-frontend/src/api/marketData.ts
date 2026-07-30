import { http } from './http'
import type {
  BatchDailyPriceSyncRequest,
  BatchDailyPriceSyncResult,
  DailyPrice,
  DailyPriceSummary,
  DailyPriceSyncRequest,
  DailyPriceSyncResult,
  ImportInstrumentsRequest,
  InstrumentCatalogItem,
  ImportPreviewItem,
  InstrumentImportPreviewRequest,
  InstrumentImportResult,
  MarketDataProviderConfig,
  UpdateMarketDataProviderConfigRequest,
} from '@/types/marketData'

const CATALOG_REQUEST_TIMEOUT_MS = 30_000
const MARKET_DATA_OPERATION_TIMEOUT_MS = 120_000

export function searchInstrumentCatalog(query: string, limit = 20) {
  return http.get('/instruments/catalog/search', {
    params: { query, limit },
    timeout: CATALOG_REQUEST_TIMEOUT_MS,
  }) as Promise<InstrumentCatalogItem[]>
}

export function previewInstrumentImport(request: InstrumentImportPreviewRequest) {
  return http.post('/instruments/import/preview', request, {
    timeout: CATALOG_REQUEST_TIMEOUT_MS,
  }) as Promise<ImportPreviewItem[]>
}

export function importInstruments(request: ImportInstrumentsRequest) {
  return http.post('/instruments/import', request, {
    timeout: MARKET_DATA_OPERATION_TIMEOUT_MS,
  }) as Promise<InstrumentImportResult[]>
}

export function syncInstrumentDailyPrices(instrumentId: number, request: DailyPriceSyncRequest) {
  return http.post(`/instruments/${instrumentId}/daily-prices/sync`, request, {
    timeout: MARKET_DATA_OPERATION_TIMEOUT_MS,
  }) as Promise<DailyPriceSyncResult>
}

export function syncMultipleInstrumentDailyPrices(request: BatchDailyPriceSyncRequest) {
  return http.post('/market-data/daily-prices/sync', request, {
    timeout: MARKET_DATA_OPERATION_TIMEOUT_MS,
  }) as Promise<BatchDailyPriceSyncResult>
}

export function getMarketDataProviderConfig() {
  return http.get('/market-data/config') as Promise<MarketDataProviderConfig>
}

export function updateMarketDataProviderConfig(request: UpdateMarketDataProviderConfigRequest) {
  return http.put('/market-data/config', request) as Promise<MarketDataProviderConfig>
}

export function listInstrumentDailyPrices(
  instrumentId: number,
  params: { from?: string; to?: string; limit?: number } = {},
) {
  return http.get(`/instruments/${instrumentId}/daily-prices`, { params }) as Promise<DailyPrice[]>
}

export function getInstrumentDailyPriceSummary(instrumentId: number) {
  return http.get(`/instruments/${instrumentId}/daily-prices/summary`) as Promise<DailyPriceSummary>
}

export function clearInstrumentDailyPrices(instrumentId: number) {
  return http.delete(`/instruments/${instrumentId}/daily-prices`) as Promise<null>
}
