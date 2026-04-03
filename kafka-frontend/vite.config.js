import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // /api/producer/... → localhost:8080/...  (prefix 제거)
      '/api/producer': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/producer/, ''),
      },
      // /api/consumer/... → localhost:8081/...  (prefix 제거)
      '/api/consumer': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api\/consumer/, ''),
      },
    },
  },
})
