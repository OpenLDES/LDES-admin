import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// The Spring Boot backend, used in production mode
const backend = process.env.LDES_ADMIN_BACKEND ?? 'http://localhost:8080'
const proxy = {
  '/api': backend,
  // Health of the backend, to pick demo or production mode on start
  '/actuator': backend,
}

export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  define: {
    // pnpm build-restrict-to-demo-mode builds in mode demo-only: a frontend for demos, without a
    // backend, that refuses production mode
    __DEMO_ONLY__: JSON.stringify(mode === 'demo-only'),
  },
  server: { proxy },
  preview: { proxy },
  build: {
    chunkSizeWarningLimit: 1500,
  },
}))
