import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      // 开发环境代理：/api 和 /ws 转发到后端 8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'http://localhost:8080',
        ws: true  // WebSocket 代理
      }
    }
  },
  resolve: {
    alias: {
      '@': '/src'  // @ 指向 src 目录
    }
  }
})
