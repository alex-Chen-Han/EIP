import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './index.css'
import axios from 'axios'

// 設定 Axios 請求攔截器，自動注入 JWT Token
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
}, error => {
  return Promise.reject(error)
})

// 設定 Axios 回應攔截器，當收到 401 Unauthorized 時自動清除 Token 並跳轉登入
axios.interceptors.response.use(response => {
  const newToken = response.headers['authorization-new'] || response.headers['Authorization-New']
  if (newToken) {
    localStorage.setItem('token', newToken)
  }
  return response
}, error => {
  if (error.response && error.response.status === 401) {
    localStorage.removeItem('token')
    localStorage.removeItem('currentUser')
    router.push('/login')
  }
  return Promise.reject(error)
})

// 覆寫瀏覽器全域的 fetch，確保 fetch 請求也會自動注入 JWT Token，並攔截 401 狀態碼
const originalFetch = window.fetch
window.fetch = async (url, options = {}) => {
  const token = localStorage.getItem('token')
  if (token) {
    options.headers = {
      ...options.headers,
      'Authorization': `Bearer ${token}`
    }
  }
  try {
    const response = await originalFetch(url, options)
    const newToken = response.headers.get('Authorization-New')
    if (newToken) {
      localStorage.setItem('token', newToken)
    }
    if (response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('currentUser')
      router.push('/login')
    }
    return response
  } catch (error) {
    throw error;
  }
}

const app = createApp(App)

// 註冊所有 Element Plus 圖示
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
