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

test('证券目录可以按代码搜索并区分同码指数和股票', async ({ page }) => {
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    if (pathname === '/api/instruments') return fulfill(route, [])
    if (pathname === '/api/instruments/catalog/search') {
      return fulfill(route, [
        {
          canonicalCode: '000688.SH',
          symbol: '000688',
          instrumentName: '科创50',
          exchange: 'SSE',
          assetType: 'INDEX',
          currency: 'CNY',
          listStatus: 'L',
          listDate: null,
          alreadyExists: false,
          existingInstrumentId: null,
        },
        {
          canonicalCode: '000688.SZ',
          symbol: '000688',
          instrumentName: '国城矿业',
          exchange: 'SZSE',
          assetType: 'STOCK',
          currency: 'CNY',
          listStatus: 'L',
          listDate: '1997-01-20',
          alreadyExists: false,
          existingInstrumentId: null,
        },
      ])
    }
    return fulfill(route, [])
  })

  await page.goto('/market-data')
  await page.getByRole('button', { name: '添加证券' }).click()
  await page
    .getByRole('dialog', { name: '添加证券' })
    .getByRole('combobox', { name: '按代码或名称搜索' })
    .fill('000688')

  const dropdown = page.locator('.el-select-dropdown:visible')
  await expect(dropdown.getByText('科创50', { exact: true })).toBeVisible()
  await expect(dropdown.getByText('000688.SH · 上交所 · 指数', { exact: true })).toBeVisible()
  await expect(dropdown.getByText('国城矿业', { exact: true })).toBeVisible()
  await expect(dropdown.getByText('000688.SZ · 深交所 · 股票', { exact: true })).toBeVisible()
})

test('证券搜索后可以完成导入预览', async ({ page }) => {
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    if (pathname === '/api/instruments') return fulfill(route, [])
    if (pathname === '/api/instruments/catalog/search') {
      return fulfill(route, [
        {
          canonicalCode: '600089.SH',
          symbol: '600089',
          instrumentName: '特变电工',
          exchange: 'SSE',
          assetType: 'STOCK',
          currency: 'CNY',
          listStatus: 'L',
          listDate: '1997-06-18',
          alreadyExists: false,
          existingInstrumentId: null,
        },
      ])
    }
    if (request.method() === 'POST' && pathname === '/api/instruments/import/preview') {
      expect(request.postDataJSON()).toEqual({ codes: ['600089.SH'] })
      return fulfill(route, [
        {
          originalInput: '600089.SH',
          canonicalCode: '600089.SH',
          symbol: '600089',
          exchange: 'SSE',
          instrumentName: '特变电工',
          currency: 'CNY',
          assetType: 'STOCK',
          lotSize: '100',
          listStatus: 'L',
          listDate: '1997-06-18',
          alreadyExists: false,
          existingInstrumentId: null,
          valid: true,
          message: 'OK',
        },
      ])
    }
    return fulfill(route, [])
  })

  await page.goto('/market-data')
  await page.getByRole('button', { name: '添加证券' }).click()
  const dialog = page.getByRole('dialog', { name: '添加证券' })
  await dialog.getByRole('combobox', { name: '按代码或名称搜索' }).fill('特变')
  await page.locator('.el-select-dropdown:visible').getByText('特变电工', { exact: true }).click()
  await dialog.getByRole('button', { name: '检查所选证券' }).click()

  const previewRow = dialog.locator('.el-table__body tr').filter({ hasText: '特变电工' })
  await expect(previewRow).toContainText('600089.SH')
  await expect(previewRow).toContainText('通过')
  await expect(page.getByText('导入预览失败，请检查后端服务。')).toHaveCount(0)
})

test('证券目录无匹配结果时显示明确空状态', async ({ page }) => {
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    return fulfill(route, [])
  })

  await page.goto('/market-data')
  await page.getByRole('button', { name: '添加证券' }).click()
  await page
    .getByRole('dialog', { name: '添加证券' })
    .getByRole('combobox', { name: '按代码或名称搜索' })
    .fill('不存在的证券')

  await expect(page.locator('.el-select-dropdown:visible')).toContainText('未找到匹配的证券')
})

test('证券目录服务不可达时显示可操作错误', async ({ page }) => {
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname
    if (!pathname.startsWith('/api/')) return route.continue()
    if (pathname === '/api/instruments/catalog/search') {
      return route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({
          success: false,
          data: null,
          message: 'Python market data service is unavailable',
        }),
      })
    }
    return fulfill(route, [])
  })

  await page.goto('/market-data')
  await page.getByRole('button', { name: '添加证券' }).click()
  await page
    .getByRole('dialog', { name: '添加证券' })
    .getByRole('combobox', { name: '按代码或名称搜索' })
    .fill('600519')

  await expect(page.locator('.el-select-dropdown:visible')).toContainText(
    '行情服务不可用，请确认 Python 行情服务已启动',
  )
})

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
