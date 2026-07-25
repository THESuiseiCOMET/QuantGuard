import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import AccountsView from '@/views/accounts/AccountsView.vue'
import OrdersView from '@/views/orders/OrdersView.vue'
import MarketWorkspaceView from '@/views/market-data/MarketWorkspaceView.vue'
import RecoveryCalculatorView from '@/views/calculator/RecoveryCalculatorView.vue'
import SettingsView from '@/views/settings/SettingsView.vue'
import NotFoundView from '@/views/placeholder/NotFoundView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      redirect: '/market-data?tab=positions',
      children: [
        { path: 'dashboard', redirect: '/market-data?tab=positions' },
        {
          path: 'accounts',
          name: 'accounts',
          component: AccountsView,
          meta: { title: '模拟账户' },
        },
        { path: 'portfolios', name: 'portfolios', redirect: '/accounts' },
        { path: 'positions', name: 'positions', redirect: '/market-data?tab=positions' },
        { path: 'orders', name: 'orders', component: OrdersView, meta: { title: '模拟订单' } },
        { path: 'trades', name: 'trades', redirect: '/orders' },
        {
          path: 'cash-ledger',
          name: 'cash-ledger',
          redirect: '/market-data?tab=positions',
        },
        {
          path: 'market-data',
          name: 'market-data',
          component: MarketWorkspaceView,
          meta: { title: '行情中心' },
        },
        {
          path: 'recovery-calculator',
          name: 'recovery-calculator',
          component: RecoveryCalculatorView,
          meta: { title: '回本计算' },
        },
        { path: 'settings', name: 'settings', component: SettingsView, meta: { title: '设置' } },
        { path: 'system', name: 'system', redirect: '/settings' },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: NotFoundView,
      meta: { title: '404' },
    },
  ],
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : ''
  document.title = title ? `${title} - QuantGuard` : 'QuantGuard'
})
