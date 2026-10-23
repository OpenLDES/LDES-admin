import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // The Spring Boot backend; not used yet, the frontend runs on sample data
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    chunkSizeWarningLimit: 1500,
  },
})
