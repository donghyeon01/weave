import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': 'http://localhost:8081',
      // STOMP raw WebSocket — 계약 §3.9. ws 업그레이드를 명시해야 한다
      '/ws': { target: 'http://localhost:8081', ws: true },
    },
  },
  // preview는 server.proxy를 물려받지 않는다 — check:preview E2E에서도 동일 프록시가 필요
  preview: {
    proxy: {
      '/api': 'http://localhost:8081',
      '/ws': { target: 'http://localhost:8081', ws: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/testing/setup.ts'],
  },
})
