<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

const currentUser = ref(null)

const loadUser = () => {
  currentUser.value = JSON.parse(localStorage.getItem('currentUser') || 'null')
}

// 每次路由改變時更新使用者資訊，確保登入狀態即時更新
router.afterEach(() => {
  loadUser()
})

onMounted(() => {
  loadUser()
})

const isLoggedIn = computed(() => !!currentUser.value)
const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')

const handleLogout = () => {
  localStorage.removeItem('currentUser')
  currentUser.value = null
  router.push('/login')
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
