import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'VITE_')
  const target = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080'
  return {
  plugins: [react()],
  server: {
    port: 5173,
    host: '127.0.0.1',
    proxy: {
      '/api': target,
      '/actuator': target,
    },
  },
  preview: {
    port: 4173,
    host: '127.0.0.1',
  },
  }
})
