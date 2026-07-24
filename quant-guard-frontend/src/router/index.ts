import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import DashboardView from '@/views/dashboard/DashboardView.vue'
import AccountsView from '@/views/accounts/AccountsView.vue'
import PortfoliosView from '@/views/portfolios/PortfoliosView.vue'
import PositionsView from '@/views/positions/PositionsView.vue'
import OrdersView from '@/views/orders/OrdersView.vue'
import RiskConfigView from '@/views/risk/RiskConfigView.vue'
import SystemView from '@/views/system/SystemView.vue'
import DevelopmentView from '@/views/placeholder/DevelopmentView.vue'
import NotFoundView from '@/views/placeholder/NotFoundView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: MainLayout,
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: DashboardView, meta: { title: '总览' } },
        {
          path: 'accounts',
          name: 'accounts',
          component: AccountsView,
          meta: { title: '模拟账户' },
        },
        {
          path: 'portfolios',
          name: 'portfolios',
          component: PortfoliosView,
          meta: { title: '投资组合' },
        },
        { path: 'positions', name: 'positions', component: PositionsView, meta: { title: '持仓' } },
        { path: 'orders', name: 'orders', component: OrdersView, meta: { title: '模拟订单' } },
        { path: 'trades', name: 'trades', component: DevelopmentView, meta: { title: '成交记录' } },
        {
          path: 'cash-ledger',
          name: 'cash-ledger',
          component: DevelopmentView,
          meta: { title: '资金流水' },
        },
        { path: 'risk', name: 'risk', component: RiskConfigView, meta: { title: '风险管理' } },
        { path: 'system', name: 'system', component: SystemView, meta: { title: '系统信息' } },
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
