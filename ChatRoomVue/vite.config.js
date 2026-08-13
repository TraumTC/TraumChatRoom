import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  css: {
    // lightningcss 无法解析含 `.5` 的转义类名，退回 esbuild minify
    transformer: 'postcss',
    minify: 'esbuild'
  },
  server: {
    port: 5173,
    // 显式允许 unload 事件（sockjs-client 在页面卸载时清理连接），消除 Chrome Permissions Policy violation 警告
    headers: {
      'Permissions-Policy': 'unload=(self)'
    },
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
