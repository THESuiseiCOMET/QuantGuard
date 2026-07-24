import { defineStore } from 'pinia'

interface PortfolioSelectionState {
  selectedAccountId: number | null
  selectedPortfolioId: number | null
}

const storageKey = 'quantguard.portfolio.selection'

function emptySelection(): PortfolioSelectionState {
  return {
    selectedAccountId: null,
    selectedPortfolioId: null,
  }
}

function loadSelection(): PortfolioSelectionState {
  try {
    const raw = window.localStorage.getItem(storageKey)
    if (!raw) return emptySelection()

    const parsed = JSON.parse(raw) as Partial<PortfolioSelectionState>
    return {
      selectedAccountId:
        typeof parsed.selectedAccountId === 'number' ? parsed.selectedAccountId : null,
      selectedPortfolioId:
        typeof parsed.selectedPortfolioId === 'number' ? parsed.selectedPortfolioId : null,
    }
  } catch {
    return emptySelection()
  }
}

export const usePortfolioStore = defineStore('portfolio', {
  state: (): PortfolioSelectionState => loadSelection(),
  actions: {
    selectAccount(accountId: number | null) {
      this.selectedAccountId = accountId
      if (accountId === null) this.selectedPortfolioId = null
      this.persist()
    },
    selectPortfolio(portfolioId: number | null) {
      this.selectedPortfolioId = portfolioId
      this.persist()
    },
    persist() {
      window.localStorage.setItem(
        storageKey,
        JSON.stringify({
          selectedAccountId: this.selectedAccountId,
          selectedPortfolioId: this.selectedPortfolioId,
        }),
      )
    },
  },
})
