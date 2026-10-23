import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '@/views/DashboardView.vue'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** The view fills the whole content area, without padding */
    fullBleed?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'dashboard', component: DashboardView, meta: { title: 'Dashboard' } },
    {
      path: '/ldes-servers',
      name: 'ldes-servers',
      component: () => import('@/views/LdesServersView.vue'),
      meta: { title: 'LDES servers' },
    },
    {
      path: '/ldes-servers/:id',
      name: 'ldes-server',
      component: () => import('@/views/LdesServerView.vue'),
      props: true,
      meta: { title: 'LDES server' },
    },
    {
      path: '/ldes-servers/:id/eventstreams/:collection',
      name: 'event-stream',
      component: () => import('@/views/EventStreamView.vue'),
      props: true,
      meta: { title: 'Event stream' },
    },
    {
      path: '/ldios',
      name: 'ldios',
      component: () => import('@/views/LdiosView.vue'),
      meta: { title: 'LDIOs' },
    },
    {
      path: '/ldios/:id',
      name: 'ldio',
      component: () => import('@/views/LdioView.vue'),
      props: true,
      meta: { title: 'LDIO' },
    },
    {
      path: '/sandbox',
      name: 'sandbox',
      component: () => import('@/views/SandboxView.vue'),
      meta: { title: 'Sandbox', fullBleed: true },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: 'Not found' },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · LDES-admin` : 'LDES-admin'
})

export default router
