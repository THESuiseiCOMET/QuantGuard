export type Exchange = 'SSE' | 'SZSE' | 'BSE' | 'HKEX' | 'NASDAQ' | 'NYSE'
export type AssetType = 'STOCK' | 'ETF' | 'INDEX'
export type InstrumentStatus = 'ACTIVE' | 'DISABLED'
export type DecimalValue = string | number

export interface SecurityInstrument {
  id: number
  symbol: string
  exchange: Exchange
  instrumentName: string
  assetType: AssetType
  currency: string
  lotSize: DecimalValue
  status: InstrumentStatus
  createdAt: string
  updatedAt: string
}
