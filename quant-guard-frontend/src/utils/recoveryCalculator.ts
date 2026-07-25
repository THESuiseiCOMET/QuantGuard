export type RecoveryCalculation = {
  additionalQuantity: number
  additionalAmount: number
  totalQuantity: number
  totalCost: number
  averageCost: number
  breakEvenGainPercent: number
}

export type QuantityRecoveryInput = {
  quantity: number
  averageCost: number
  currentPrice: number
  additionalQuantity: number
  additionalPrice: number
}

export type TargetRecoveryInput = {
  quantity: number
  averageCost: number
  currentPrice: number
  additionalPrice: number
  targetGainPercent: number
  lotSize: number
}

export type TargetRecoveryCalculation = RecoveryCalculation & {
  targetPrice: number
  rawAdditionalQuantity: number
}

export type PositionRecoveryStatus = {
  state: 'INVALID' | 'BALANCED' | 'PROFITABLE' | 'LOSING'
  profitAmount: number
  profitPercent: number
  breakEvenGainPercent: number
}

export function calculatePositionRecoveryStatus(
  quantity: number,
  averageCost: number,
  currentPrice: number,
): PositionRecoveryStatus {
  if (!isPositive(quantity) || !isPositive(averageCost) || !isPositive(currentPrice)) {
    return {
      state: 'INVALID',
      profitAmount: 0,
      profitPercent: 0,
      breakEvenGainPercent: 0,
    }
  }

  const difference = currentPrice - averageCost
  const tolerance = Math.max(1, averageCost) * 1e-8
  const state =
    Math.abs(difference) <= tolerance ? 'BALANCED' : difference > 0 ? 'PROFITABLE' : 'LOSING'

  return {
    state,
    profitAmount: difference * quantity,
    profitPercent: (difference / averageCost) * 100,
    breakEvenGainPercent: Math.max(0, ((averageCost - currentPrice) / currentPrice) * 100),
  }
}

export function calculateRecoveryByQuantity(
  input: QuantityRecoveryInput,
): RecoveryCalculation | null {
  if (
    !isPositive(input.quantity) ||
    !isPositive(input.averageCost) ||
    !isPositive(input.currentPrice) ||
    !isNonNegative(input.additionalQuantity) ||
    !isPositive(input.additionalPrice)
  ) {
    return null
  }

  const totalQuantity = input.quantity + input.additionalQuantity
  const totalCost =
    input.quantity * input.averageCost + input.additionalQuantity * input.additionalPrice
  const averageCost = totalCost / totalQuantity

  return {
    additionalQuantity: input.additionalQuantity,
    additionalAmount: input.additionalQuantity * input.additionalPrice,
    totalQuantity,
    totalCost,
    averageCost,
    breakEvenGainPercent: ((averageCost - input.currentPrice) / input.currentPrice) * 100,
  }
}

export function calculateRecoveryByTarget(
  input: TargetRecoveryInput,
): TargetRecoveryCalculation | null {
  if (
    !isPositive(input.quantity) ||
    !isPositive(input.averageCost) ||
    !isPositive(input.currentPrice) ||
    !isPositive(input.additionalPrice) ||
    !isNonNegative(input.targetGainPercent) ||
    !isPositive(input.lotSize)
  ) {
    return null
  }

  const targetPrice = input.currentPrice * (1 + input.targetGainPercent / 100)
  if (input.averageCost <= targetPrice) {
    const result = calculateRecoveryByQuantity({
      ...input,
      additionalQuantity: 0,
    })
    return result ? { ...result, targetPrice, rawAdditionalQuantity: 0 } : null
  }

  if (targetPrice <= input.additionalPrice) return null

  const rawAdditionalQuantity =
    (input.quantity * (input.averageCost - targetPrice)) / (targetPrice - input.additionalPrice)
  const additionalQuantity = roundUpToLot(rawAdditionalQuantity, input.lotSize)
  const result = calculateRecoveryByQuantity({
    ...input,
    additionalQuantity,
  })

  return result ? { ...result, targetPrice, rawAdditionalQuantity } : null
}

export function roundUpToLot(quantity: number, lotSize: number) {
  if (!isNonNegative(quantity) || !isPositive(lotSize)) return 0
  return Math.ceil(Math.max(0, quantity - Number.EPSILON) / lotSize) * lotSize
}

function isPositive(value: number) {
  return Number.isFinite(value) && value > 0
}

function isNonNegative(value: number) {
  return Number.isFinite(value) && value >= 0
}
