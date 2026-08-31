// src/main.js — 应用入口
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import VueVirtualScroller from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
import router from './router'
import App from './App.vue'
import './style.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// Naive UI 不做全局注册：app.use(naive) 会把整个组件库塞进启动路径，
// 树摇完全失效（主包曾达 1.33 MB）。改为各组件在 <script setup> 里按需 import ——
// 模板里的 <n-xxx> 会自动解析到导入的 NXxx，因此模板无需改动，
// 且 admin 专用的重组件（NDataTable / NDatePicker 等）会随懒加载路由移出主包。
// App.vue 的 4 个 Provider（Config/Message/Dialog/Notification）本来就是显式导入。
// 虚拟滚动（RecycleScroller）
app.use(VueVirtualScroller)

app.mount('#app')
