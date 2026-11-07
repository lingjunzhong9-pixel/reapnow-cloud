import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 统一走网关：前端只认一个入口，后端服务实例增减对前端透明
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5174,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})