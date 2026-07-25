import { expect, test } from '@playwright/test'
import type { Page, Route } from '@playwright/test'

type AccountRow = {
  id: number
  accountName: string
  accountType: 'LONG_TERM' | 'SHORT_TERM'
  initialCash: string
  availableCash: string
  frozenCash: string
  status: 'ACTIVE' | 'DISABLED'
  createdAt: string
  updatedAt: string
}

const timestamp = '2026-07-25T12:00:00'

test('K 线可视区变化会进入自定义区间并更新范围高低价', async ({ page }) => {
  const prices = createDailyPrices(320)
  await mockMarketApi(page, prices)
  await page.goto('/market-data')

  await page.locator('.range-tools').getByText('全部', { exact: true }).click()
  await expect(page.getByRole('radio', { name: '全部' })).toBeChecked()

  const chart = page.getByTestId('candlestick-chart').first()
  await expect(chart).toBeVisible()
  const initialHigh = await page.getByTestId('range-high').textContent()
  await page.evaluate(async () => {
    const moduleUrl = performance
      .getEntriesByType('resource')
      .map((entry) => entry.name)
      .find((url) => url.includes('/echarts_core.js'))
    if (!moduleUrl) throw new Error('ECharts core module was not loaded')
    const echarts = (await import(moduleUrl)) as {
      getInstanceByDom: (element: HTMLElement) => {
        dispatchAction: (action: { type: string; start: number; end: number }) => void
      } | null
    }
    const chartElement = document.querySelector<HTMLElement>('[data-testid="candlestick-chart"]')
    if (!chartElement) throw new Error('K line chart element was not found')
    const instance = echarts.getInstanceByDom(chartElement)
    if (!instance) throw new Error('K line chart instance was not found')
    instance.dispatchAction({ type: 'dataZoom', start: 0, end: 50 })
  })

  await expect(page.getByRole('radio', { name: '自定义' })).toBeChecked()
  await expect(page.getByTestId('range-high')).not.toHaveText(initialHigh ?? '')
})

test('切换账户后硬删除当前账户会清理选择并回退到可用账户', async ({ page }) => {
  let accounts: AccountRow[] = [
    createAccount(1, '长期账户', 'LONG_TERM'),
    createAccount(2, '短线账户', 'SHORT_TERM'),
  ]
  const portfolios = [createPortfolio(101, 1), createPortfolio(202, 2)]

  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    if (request.method() === 'GET' && pathname === '/api/accounts') {
      return fulfill(route, accounts)
    }
    if (request.method() === 'GET' && pathname === '/api/portfolios') {
      return fulfill(
        route,
        portfolios.filter((item) => accounts.some((a) => a.id === item.accountId)),
      )
    }
    if (request.method() === 'DELETE' && pathname === '/api/accounts/2/force') {
      accounts = accounts.filter((account) => account.id !== 2)
      return fulfill(route, null)
    }
    return fulfill(route, [])
  })

  await page.goto('/accounts')
  const shortRow = page.locator('.el-table__body tr').filter({ hasText: '短线账户' })
  await shortRow.getByRole('button', { name: '使用账户' }).click()
  await expect(shortRow.getByText('当前', { exact: true })).toBeVisible()

  const selectedBeforeDelete = await selectedAccountIds(page)
  expect(selectedBeforeDelete).toEqual({ selectedAccountId: 2, selectedPortfolioId: 202 })

  await shortRow.getByRole('button', { name: '更多' }).click()
  await page
    .locator('.el-dropdown-menu:visible')
    .getByText('删除账户及模拟数据', { exact: true })
    .click()
  await page.getByRole('dialog').getByRole('button', { name: '硬删除' }).click()

  await expect(page.getByText('短线账户', { exact: true })).toHaveCount(0)
  const remainingRow = page.locator('.el-table__body tr').filter({ hasText: '长期账户' })
  await expect(remainingRow).toHaveCount(1)
  await expect(remainingRow.getByText('当前', { exact: true })).toBeVisible()
  const selectedAfterDelete = await selectedAccountIds(page)
  expect(selectedAfterDelete).toEqual({ selectedAccountId: 1, selectedPortfolioId: 101 })
})

test('亏损持仓可以按数量或目标涨幅计算补仓回本', async ({ page }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem(
      'quantguard.portfolio.selection',
      JSON.stringify({ selectedAccountId: 1, selectedPortfolioId: 101 }),
    )
  })
  await mockPositionApi(page)
  await page.goto('/market-data?tab=positions')

  const positionCard = page.locator('.position-card').filter({ hasText: '测试股票' })
  await positionCard.getByRole('button', { name: '回本计算' }).click()
  const dialog = page.getByRole('dialog', { name: 'TEST 回本计算' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText('39.0909', { exact: true })).toBeVisible()
  await expect(dialog.getByText('30.3%', { exact: true })).toBeVisible()

  await dialog.getByText('按目标涨幅', { exact: true }).click()
  await expect(dialog.getByText('5,700', { exact: true })).toBeVisible()
  await expect(dialog.getByText('171,000.00', { exact: true })).toBeVisible()
  await expect(dialog.getByText('4.98%', { exact: true })).toBeVisible()
})

test('盈亏平衡持仓仍显示回本计算并允许修改当前价格', async ({ page }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem(
      'quantguard.portfolio.selection',
      JSON.stringify({ selectedAccountId: 1, selectedPortfolioId: 101 }),
    )
  })
  await mockPositionApi(page, '40')
  await page.goto('/market-data?tab=positions')

  const positionCard = page.locator('.position-card').filter({ hasText: '测试股票' })
  const recoveryButton = positionCard.getByRole('button', { name: '回本计算' })
  await expect(recoveryButton).toBeVisible()
  await recoveryButton.click()

  const dialog = page.getByRole('dialog', { name: 'TEST 回本计算' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByText('当前处于盈亏平衡，可继续测算不同补仓方案。')).toBeVisible()

  await dialog.getByRole('spinbutton', { name: '当前价格' }).fill('35')
  await expect(dialog.getByText(/当前亏损 5,000\.00/)).toBeVisible()
  await expect(dialog.getByText(/回本需要上涨 14\.29%/)).toBeVisible()
})

test('左侧独立回本计算器支持自定义价格和三种持仓状态', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/recovery-calculator')

  await expect(page.getByRole('heading', { name: '回本计算' })).toBeVisible()
  await expect(page.getByText(/当前亏损 12\.5%/)).toBeVisible()
  await expect(page.getByText('39.5455', { exact: true })).toBeVisible()

  const currentPrice = page.getByRole('spinbutton', { name: '当前价格' })
  await currentPrice.fill('40')
  await expect(page.getByText('当前处于盈亏平衡，可继续测算不同补仓方案。')).toBeVisible()

  await currentPrice.fill('45')
  await expect(
    page.getByText('当前已经回本并处于盈利状态，以下结果仅用于持仓成本测算。'),
  ).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    ),
  ).toBe(false)
})

async function mockMarketApi(page: Page, prices: ReturnType<typeof createDailyPrices>) {
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    if (pathname === '/api/instruments') return fulfill(route, [createInstrument()])
    if (pathname === '/api/instruments/1/daily-prices') return fulfill(route, prices)
    if (pathname === '/api/instruments/1/daily-prices/summary') {
      const latest = prices[prices.length - 1]
      return fulfill(route, {
        instrumentId: 1,
        firstTradeDate: prices[0]?.tradeDate,
        latestTradeDate: latest?.tradeDate,
        recordCount: prices.length,
        latestClose: latest?.close,
        latestPercentageChange: latest?.percentageChange,
        periodHigh: Math.max(...prices.map((price) => Number(price.high))),
        periodLow: Math.min(...prices.map((price) => Number(price.low))),
        lastSyncAt: timestamp,
        dataSource: 'TEST',
      })
    }
    return fulfill(route, [])
  })
}

async function mockPositionApi(page: Page, latestClose = '30') {
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    if (pathname === '/api/instruments') return fulfill(route, [createInstrument()])
    if (pathname === '/api/portfolios/101/positions') {
      return fulfill(route, [
        {
          id: 1,
          portfolioId: 101,
          instrumentId: 1,
          totalQuantity: '1000',
          availableQuantity: '1000',
          frozenQuantity: '0',
          averageCost: '40',
          createdAt: timestamp,
          updatedAt: timestamp,
        },
      ])
    }
    if (pathname === '/api/instruments/1/daily-prices/summary') {
      return fulfill(route, {
        instrumentId: 1,
        firstTradeDate: '2026-01-01',
        latestTradeDate: '2026-07-25',
        recordCount: 1,
        latestClose,
        latestPercentageChange: '-2',
        periodHigh: '30',
        periodLow: '30',
        lastSyncAt: timestamp,
        dataSource: 'TEST',
      })
    }
    if (pathname === '/api/instruments/1/daily-prices') {
      return fulfill(route, createDailyPrices(20))
    }
    return fulfill(route, [])
  })
}

function createDailyPrices(count: number) {
  return Array.from({ length: count }, (_, index) => {
    const close = 10 + index * 0.1
    const previousClose = close - 0.05
    return {
      id: index + 1,
      instrumentId: 1,
      tradeDate: new Date(Date.UTC(2025, 0, index + 1)).toISOString().slice(0, 10),
      open: close - 0.2,
      high: close + 1,
      low: close - 1,
      close,
      previousClose,
      change: close - previousClose,
      percentageChange: ((close - previousClose) / previousClose) * 100,
      volumeLots: 1000 + index,
      amountCny: (1000 + index) * close,
      adjustmentType: 'NONE',
      dataSource: 'TEST',
    }
  })
}

function createAccount(
  id: number,
  accountName: string,
  accountType: AccountRow['accountType'],
): AccountRow {
  return {
    id,
    accountName,
    accountType,
    initialCash: '1000000',
    availableCash: '1000000',
    frozenCash: '0',
    status: 'ACTIVE',
    createdAt: timestamp,
    updatedAt: timestamp,
  }
}

function createPortfolio(id: number, accountId: number) {
  return {
    id,
    accountId,
    portfolioName: '默认组合',
    portfolioType: accountId === 1 ? 'LONG_TERM' : 'SHORT_TERM',
    description: null,
    status: 'ACTIVE',
    createdAt: timestamp,
    updatedAt: timestamp,
  }
}

function createInstrument() {
  return {
    id: 1,
    symbol: 'TEST',
    exchange: 'SSE',
    instrumentName: '测试股票',
    assetType: 'STOCK',
    currency: 'CNY',
    lotSize: 100,
    status: 'ACTIVE',
    createdAt: timestamp,
    updatedAt: timestamp,
  }
}

async function selectedAccountIds(page: Page) {
  return page.evaluate(() => {
    const value = window.localStorage.getItem('quantguard.portfolio.selection')
    return value ? JSON.parse(value) : null
  })
}

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ success: true, data, message: 'OK' }),
  })
}
