import type { AssetType, DecimalValue, Exchange } from './instrument'

export interface InstrumentImportPreviewRequest {
  codes: string[]
}

export interface ImportInstrumentsRequest {
  codes: string[]
  syncDailyPrices: boolean
  startDate?: string | null
}

export interface DailyPriceSyncRequest {
  startDate?: string | null
  endDate?: string | null
  fullRefresh: boolean
}

export interface BatchDailyPriceSyncRequest {
  instrumentIds?: number[]
  syncAllImportedInstruments?: boolean
}

export interface ImportPreviewItem {
  originalInput: string
  canonicalCode: string | null
  symbol: string | null
  exchange: Exchange | null
  instrumentName: string | null
  currency: string | null
  assetType: AssetType
  lotSize: DecimalValue | null
  listStatus: string | null
  listDate: string | null
  alreadyExists: boolean
  existingInstrumentId: number | null
  valid: boolean
  message: string
}

export type InstrumentImportAction = 'CREATED' | 'UPDATED' | 'UNCHANGED' | 'FAILED'
export type MarketDataSyncStatus = 'SUCCESS' | 'PARTIAL_SUCCESS' | 'FAILED' | 'SKIPPED'

export interface InstrumentImportResult {
  canonicalCode: string | null
  instrumentId: number | null
  instrumentName: string | null
  action: InstrumentImportAction
  metadataStatus: string
  priceSyncStatus: MarketDataSyncStatus
  recordsInserted: number
  recordsUpdated: number
  latestTradeDate: string | null
  message: string
}

export interface DailyPrice {
  id: number
  instrumentId: number
  tradeDate: string
  open: DecimalValue
  high: DecimalValue
  low: DecimalValue
  close: DecimalValue
  previousClose: DecimalValue
  change: DecimalValue
  percentageChange: DecimalValue
  volumeLots: DecimalValue
  amountCny: DecimalValue
  adjustmentType: 'NONE'
  dataSource: string
}

export interface DailyPriceSummary {
  instrumentId: number
  firstTradeDate: string | null
  latestTradeDate: string | null
  recordCount: number
  latestClose: DecimalValue | null
  latestPercentageChange: DecimalValue | null
  periodHigh: DecimalValue | null
  periodLow: DecimalValue | null
  lastSyncAt: string | null
  dataSource: string | null
}

export interface DailyPriceSyncResult {
  instrumentId: number
  canonicalCode: string
  status: MarketDataSyncStatus
  recordsReceived: number
  recordsInserted: number
  recordsUpdated: number
  latestTradeDate: string | null
  message: string
}

export interface BatchDailyPriceSyncResult {
  successCount: number
  failedCount: number
  skippedCount: number
  items: DailyPriceSyncResult[]
}

export interface MarketDataProviderConfig {
  provider: string
  tokenConfigured: boolean
}

export interface UpdateMarketDataProviderConfigRequest {
  token: string
}
