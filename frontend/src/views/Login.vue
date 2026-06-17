<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)

const form = reactive({
  userId: '',
  password: ''
})

const rules = {
  userId: [{ required: true, message: '請輸入員工編號', trigger: 'blur' }],
  password: [{ required: true, message: '請輸入密碼', trigger: 'blur' }]
}

const loginFormRef = ref(null)

const handleLogin = async () => {
  if (!loginFormRef.value) return
  
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const response = await axios.post('/api/auth/login', {
          userId: form.userId,
          password: form.password
        })
        
        ElMessage({
          message: '登入成功！歡迎回來，' + response.data.realName,
          type: 'success'
        })
        
        localStorage.setItem('currentUser', JSON.stringify(response.data))
        router.push('/')
      } catch (error) {
        ElMessage.error(error.response?.data || '登入失敗，請檢查帳號密碼')
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<template>
  <div class="login-wrapper">
    <div class="glass-panel login-card animate-fade-in-up">
      <div class="login-header">
        <el-icon class="logo-icon" :size="38"><Compass /></el-icon>
        <h1 class="logo-title text-gradient">EIP 電子簽核系統</h1>
        <p class="logo-subtitle">中小企業動態流程管理 MVP</p>
      </div>

      <el-form :model="form" :rules="rules" ref="loginFormRef" label-position="top">
        <el-form-item label="員工編號" prop="userId">
          <el-input 
            v-model="form.userId" 
            placeholder="請輸入員工編號 (例: EMP001)"
            prefix-icon="User"
            clearable
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item label="密碼" prop="password">
          <el-input 
            v-model="form.password" 
            type="password" 
            placeholder="請輸入密碼"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-button 
          type="primary" 
          class="login-btn" 
          :loading="loading" 
          @click="handleLogin"
        >
          登入系統
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  width: 100%;
  padding: 20px;
  background: radial-gradient(circle at top left, rgba(99, 102, 241, 0.15) 0%, rgba(248, 250, 252, 0) 60%),
              radial-gradient(circle at bottom right, rgba(6, 182, 212, 0.1) 0%, rgba(248, 250, 252, 0) 60%),
              var(--bg-primary);
}

.login-card {
  width: 100%;
  max-width: 440px;
  padding: 40px;
  border-radius: var(--radius-lg);
  box-shadow: 0 20px 40px -15px rgba(99, 102, 241, 0.15);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.logo-icon {
  color: var(--primary);
  margin-bottom: 12px;
}

.logo-title {
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 0.5px;
}

.logo-subtitle {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 6px;
}

.el-form-item {
  margin-bottom: 22px;
}

:deep(.el-form-item__label) {
  font-family: var(--font-title);
  font-weight: 500;
  color: var(--text-main);
  padding-bottom: 6px !important;
}

:deep(.el-input__wrapper) {
  border-radius: var(--radius-sm);
  padding: 8px 12px;
}

.login-btn {
  width: 100%;
  padding: 20px 0 !important;
  font-size: 15px;
  font-weight: 600;
  border-radius: var(--radius-sm) !important;
  margin-top: 10px;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.2);
}
</style>
