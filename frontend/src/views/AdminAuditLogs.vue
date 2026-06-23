<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()
const currentUser = ref(null)
const auditLogs = ref([])
const loading = ref(false)

const loadData = async () => {
  const user = localStorage.getItem('currentUser')
  if (!user) {
    router.push('/login')
    return
  }
  currentUser.value = JSON.parse(user)
  if (currentUser.value.role !== 'ADMIN') {
    ElMessage.error('無管理員權限，拒絕存取')
    router.push('/')
    return
  }

  fetchLogs()
}

const fetchLogs = async () => {
  loading.value = true
  try {
    const headers = { 'X-User-Id': currentUser.value.userId }
    const response = await axios.get('/api/audit-logs', { headers })
    auditLogs.value = response.data
  } catch (error) {
    ElMessage.error(error.response?.data || '載入審計日誌失敗')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})

const handleExport = async () => {
  try {
    const headers = { 'X-User-Id': currentUser.value.userId }
    const response = await axios.get('/api/audit-logs/export', {
      headers,
      responseType: 'blob'
    })

    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' })
    const link = document.createElement('a')
    link.href = window.URL.createObjectURL(blob)
    link.setAttribute('download', `audit_logs_${new Date().toISOString().substring(0, 10)}.csv`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    ElMessage.success('操作日誌 CSV 檔案下載成功！')
  } catch (error) {
    ElMessage.error('導出失敗')
  }
}

const getActionLabel = (type) => {
  const map = {
    'CREATE_USER': '建立帳號',
    'UPDATE_USER': '修改資料',
    'UPDATE_PASSWORD': '變更密碼',
    'DELETE_USER': '刪除帳號',
    'EXPORT_LOGS': '匯出日誌'
  }
  return map[type] || type
}

const getActionTag = (type) => {
  const map = {
    'CREATE_USER': 'success',
    'UPDATE_USER': 'primary',
    'UPDATE_PASSWORD': 'warning',
    'DELETE_USER': 'danger',
    'EXPORT_LOGS': 'info'
  }
  return map[type] || 'info'
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
}
</script>

<template>
  <div class="audit-logs-wrapper animate-fade-in">
    <div class="admin-header-row">
      <div class="header-left">
        <h2 class="admin-title text-gradient">系統審計日誌</h2>
        <p class="admin-subtitle">檢視並匯出管理員（ADMIN）在系統中進行的使用者 CRUD 敏感行為與匯出操作歷史</p>
      </div>
      <el-button type="success" icon="Download" @click="handleExport">匯出 CSV 檔</el-button>
    </div>

    <!-- 審計日誌表格 -->
    <el-card class="table-card">
      <el-table :data="auditLogs" style="width: 100%" v-loading="loading" empty-text="目前無任何審計日誌紀錄">
        <el-table-column prop="logId" label="流水號" width="90" align="center" />
        <el-table-column prop="operator.userId" label="操作管理員 ID" width="130" />
        <el-table-column prop="operator.realName" label="姓名" width="110" />
        <el-table-column label="操作類型" width="130" align="center">
          <template #default="scope">
            <el-tag :type="getActionTag(scope.row.actionType)" effect="light">
              {{ getActionLabel(scope.row.actionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetId" label="受影響對象 ID" width="130" />
        <el-table-column prop="description" label="詳細變更說明" min-width="220" />
        <el-table-column label="操作時間" width="180" align="center">
          <template #default="scope">
            {{ formatDate(scope.row.actionTime) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.audit-logs-wrapper {
  padding-top: 10px;
}

.admin-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.admin-title {
  font-size: 22px;
  font-weight: 800;
  margin-bottom: 4px;
}

.admin-subtitle {
  font-size: 13px;
  color: var(--text-muted);
}

.table-card {
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}
</style>
