<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()

const currentUser = ref(null)
const showTimeoutDialog = ref(false)
const countdownSeconds = ref(0)
const refreshing = ref(false)
let checkInterval = null
let countdownInterval = null

// 解析 JWT Payload
const parseJwt = (token) => {
  try {
    return JSON.parse(atob(token.split('.')[1]))
  } catch (e) {
    return null
  }
}

// 格式化倒數計時顯示 (例如: 2 分 30 秒)
const formatCountdown = computed(() => {
  const m = Math.floor(countdownSeconds.value / 60)
  const s = countdownSeconds.value % 60
  return `${m} 分 ${s < 10 ? '0' : ''}${s} 秒`
})

const checkTokenTimeout = () => {
  const token = localStorage.getItem('token')
  if (!token) return

  const payload = parseJwt(token)
  if (!payload || !payload.exp) return

  // 取得過期時間與目前時間的差值 (秒)
  const expireTime = payload.exp * 1000
  const now = Date.now()
  const diffSeconds = Math.floor((expireTime - now) / 1000)

  // 如果已經過期
  if (diffSeconds <= 0) {
    stopTimers()
    forceLogout()
    return
  }

  // 當剩餘時間少於 180 秒 (3 分鐘) 且目前沒在顯示 Dialog
  if (diffSeconds <= 180 && !showTimeoutDialog.value) {
    countdownSeconds.value = diffSeconds
    showTimeoutDialog.value = true
    
    // 啟動倒數計時每秒減 1
    countdownInterval = setInterval(() => {
      countdownSeconds.value--
      if (countdownSeconds.value <= 0) {
        clearInterval(countdownInterval)
        forceLogout()
      }
    }, 1000)
  } else if (diffSeconds > 180 && showTimeoutDialog.value) {
    // 如果 token 因其他操作在背後被刷除了，自動關閉 Dialog
    showTimeoutDialog.value = false
    if (countdownInterval) clearInterval(countdownInterval)
  }
}

const startTimers = () => {
  if (checkInterval) clearInterval(checkInterval)
  // 每 5 秒檢查一次 Token 狀態
  checkInterval = setInterval(checkTokenTimeout, 5000)
}

const stopTimers = () => {
  if (checkInterval) clearInterval(checkInterval)
  if (countdownInterval) clearInterval(countdownInterval)
  checkInterval = null
  countdownInterval = null
}

const keepSession = async () => {
  refreshing.value = true
  try {
    const token = localStorage.getItem('token')
    const response = await axios.post('/api/auth/refresh', {}, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    })
    
    // 儲存新 Token
    localStorage.setItem('token', response.data.token)
    ElMessage.success('登入效期已成功延長')
    showTimeoutDialog.value = false
    if (countdownInterval) clearInterval(countdownInterval)
  } catch (error) {
    ElMessage.error('維持登入失敗，請重新登入')
    forceLogout()
  } finally {
    refreshing.value = false
  }
}

const forceLogout = () => {
  showTimeoutDialog.value = false
  localStorage.removeItem('token')
  localStorage.removeItem('currentUser')
  currentUser.value = null
  stopTimers()
  router.push('/login')
}

const loadUser = () => {
  currentUser.value = JSON.parse(localStorage.getItem('currentUser') || 'null')
  if (currentUser.value) {
    startTimers()
  } else {
    stopTimers()
  }
}

// 每次路由改變時更新使用者資訊，確保登入狀態即時更新
router.afterEach(() => {
  loadUser()
})

onMounted(() => {
  loadUser()
})

onUnmounted(() => {
  stopTimers()
})

const isLoggedIn = computed(() => !!currentUser.value)
const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')

const handleLogout = () => {
  forceLogout()
}
</script>

<template>
  <div class="app-container">
    <!-- 頂部精緻導航欄 -->
    <header v-if="isLoggedIn" class="glass-panel nav-header">
      <div class="nav-left">
        <el-icon class="logo-icon" :size="24"><Compass /></el-icon>
        <span class="logo-text text-gradient">EIP 電子簽核系統</span>
      </div>

      <nav class="nav-menu">
        <router-link to="/" class="nav-item" :class="{ active: route.path === '/' }">
          <el-icon><Menu /></el-icon> 簽核工作區
        </router-link>
        <router-link to="/form-submit" class="nav-item" :class="{ active: route.path === '/form-submit' }">
          <el-icon><DocumentAdd /></el-icon> 發起簽呈
        </router-link>
        <router-link v-if="isAdmin" to="/admin/users" class="nav-item" :class="{ active: route.path === '/admin/users' }">
          <el-icon><User /></el-icon> 員工帳號管理
        </router-link>
        <router-link v-if="isAdmin" to="/admin/audit-logs" class="nav-item" :class="{ active: route.path === '/admin/audit-logs' }">
          <el-icon><Files /></el-icon> 系統操作日誌
        </router-link>
      </nav>

      <div class="nav-right">
        <div class="user-badge" v-if="currentUser">
          <span class="user-dept">{{ currentUser.department?.deptName }}</span>
          <span class="user-position">{{ currentUser.position }}</span>
          <span class="user-name">{{ currentUser.realName }}</span>
        </div>
        <el-button type="danger" plain size="small" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon> 登出
        </el-button>
      </div>
    </header>

    <main class="main-content" :class="{ 'no-nav': !isLoggedIn }">
      <router-view />
    </main>

    <!-- 登入即將過期警告彈窗 -->
    <el-dialog
      title="⚠️ 登入即將過期"
      v-model="showTimeoutDialog"
      width="400px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
      align-center
    >
      <div style="text-align: center; padding: 10px 0;">
        <p style="font-size: 15px; margin-bottom: 15px; color: var(--text-main);">
          您的登入狀態即將在 <strong style="color: var(--danger); font-size: 18px;">{{ formatCountdown }}</strong> 後過期。
        </p>
        <p style="font-size: 13px; color: var(--text-muted);">
          請點擊「維持登入」以繼續操作，避免您正在填寫的資料遺失。
        </p>
      </div>
      <template #footer>
        <div style="display: flex; gap: 12px; justify-content: center;">
          <el-button @click="forceLogout" type="info" plain>立即登出</el-button>
          <el-button @click="keepSession" type="primary" :loading="refreshing">維持登入</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.app-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  margin: 16px 24px 0 24px;
  border-radius: var(--radius-md);
  z-index: 100;
  box-shadow: var(--shadow-sm);
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  color: var(--primary);
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.nav-menu {
  display: flex;
  align-items: center;
  gap: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  text-decoration: none;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 500;
  border-radius: var(--radius-sm);
  transition: all var(--transition-fast);
}

.nav-item:hover {
  background-color: var(--primary-light);
  color: var(--primary);
}

.nav-item.active {
  background-color: var(--primary);
  color: #ffffff;
  font-weight: 600;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  background-color: #f1f5f9;
  padding: 6px 12px;
  border-radius: var(--radius-full);
  font-size: 13px;
  border: 1px solid var(--border-color);
}

.user-dept {
  font-weight: 600;
  color: var(--primary);
}

.user-position {
  color: var(--text-muted);
  border-left: 1px solid var(--border-color);
  padding-left: 8px;
}

.user-name {
  font-weight: 600;
  color: var(--text-main);
  border-left: 1px solid var(--border-color);
  padding-left: 8px;
}

.main-content {
  flex: 1;
  padding: 24px;
  max-width: 1300px;
  width: 100%;
  margin: 0 auto;
  animation: fadeIn var(--transition-normal) forwards;
}

.main-content.no-nav {
  padding: 0;
  max-width: 100%;
}
</style>
