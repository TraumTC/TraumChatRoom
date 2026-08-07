// src/main.js — 应用入口
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import naive from 'naive-ui'
import router from './router'
import App from './App.vue'
import './style.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 全局注册 Naive UI 组件（<n-input>/<n-button>/<n-alert> 等），否则组件不渲染、输入框不可用
app.use(naive)

app.mount('#app')
