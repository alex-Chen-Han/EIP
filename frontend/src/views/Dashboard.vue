<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const currentUser = ref(null)

const pendingList = ref([])
const reviewedList = ref([])
const myList = ref([])

const activeTab = ref('pending')
const detailDialogVisible = ref(false)
const selectedForm = ref(null)
const comment = ref('')
const submitLoading = ref(false)

const loadUser = () => {
  const user = localStorage.getItem('currentUser')
  if (!user) {
    router.push('/login')
    return
  }
  currentUser.value = JSON.parse(user)
}

const fetchForms = async () => {
  if (!currentUser.value) return
  const headers = { 'X-User-Id': currentUser.value.userId }
  
  try {
    const [pRes, rRes, mRes] = await Promise.all([
      axios.get('/api/forms/pending', { headers }),
      axios.get('/api/forms/reviewed', { headers }),
      axios.get('/api/forms/my', { headers })
    ])
    pendingList.value = pRes.data
    reviewedList.value = rRes.data
    myList.value = mRes.data
  } catch (error) {
    ElMessage.error('載入簽呈列表失敗')
  }
}

onMounted(() => {
  loadUser()
  fetchForms()
})

const getFormTypeLabel = (type) => {
  const map = {
    'ADVANCE': '預支請款單',
    'PAYMENT': '墊付請款單',
    'LEAVE': '請假申請單',
    'OVERTIME': '加班申請單'
  }
  return map[type] || type
}

const getFormTypeTag = (type) => {
  const map = {
    'ADVANCE': 'warning',
    'PAYMENT': 'danger',
    'LEAVE': 'success',
    'OVERTIME': 'info'
  }
  return map[type] || ''
}

const getStatusLabel = (status) => {
  const map = {
    'UNDER_REVIEW': '審核中',
    'APPROVED': '已核准',
    'REJECTED': '已駁回',
    'WITHDRAWN': '已撤回'
  }
  return map[status] || status
}

const getStatusTag = (status) => {
  const map = {
    'UNDER_REVIEW': 'warning',
    'APPROVED': 'success',
    'REJECTED': 'danger',
    'WITHDRAWN': 'info'
  }
  return map[status] || ''
}

const getRouteStatusLabel = (status) => {
  const map = {
    'WAITING': '等待中',
    'PENDING': '審核中',
    'APPROVED': '同意',
    'REJECTED': '駁回'
  }
  return map[status] || status
}

const getRouteStatusTag = (status) => {
  const map = {
    'WAITING': 'info',
    'PENDING': 'warning',
    'APPROVED': 'success',
    'REJECTED': 'danger'
  }
  return map[status] || ''
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const formatSimpleDate = (dateStr) => {
  if (!dateStr) return '-'
  return dateStr.substring(0, 10)
}

// 檢視簽呈詳情
const showDetail = async (formId) => {
  try {
    const headers = { 'X-User-Id': currentUser.value.userId }
    const response = await axios.get(`/api/forms/${formId}`, { headers })
    selectedForm.value = response.data
    comment.value = ''
    detailDialogVisible.value = true
  } catch (error) {
    ElMessage.error(error.response?.data || '載入詳情失敗，您無權限存取此簽呈。')
  }
}

// 主管審批：同意
const handleApprove = async () => {
  if (!selectedForm.value) return
  submitLoading.value = true
  try {
    const headers = { 'X-User-Id': currentUser.value.userId }
    await axios.post(`/api/forms/${selectedForm.value.formId}/approve`, {
      comment: comment.value || '同意'
    }, { headers })

    ElMessage.success('審核同意送出成功')
    detailDialogVisible.value = false
    fetchForms()
  } catch (error) {
    ElMessage.error(error.response?.data || '審核操作失敗')
  } finally {
    submitLoading.value = false
  }
}

// 主管審批：駁回
const handleReject = async () => {
  if (!selectedForm.value) return
  if (!comment.value.trim()) {
    ElMessage.warning('駁回簽呈時必須填寫意見')
    return
  }
  
  submitLoading.value = true
  try {
    const headers = { 'X-User-Id': currentUser.value.userId }
    await axios.post(`/api/forms/${selectedForm.value.formId}/reject`, {
      comment: comment.value
    }, { headers })

    ElMessage.success('已駁回此簽呈')
    detailDialogVisible.value = false
    fetchForms()
  } catch (error) {
    ElMessage.error(error.response?.data || '審核操作失敗')
  } finally {
    submitLoading.value = false
  }
}

// 申請人抽單
const handleWithdraw = async () => {
  if (!selectedForm.value) return
  
  ElMessageBox.confirm('確定要撤回（抽單）此簽呈嗎？撤回後此單將作廢。', '撤回簽呈', {
    confirmButtonText: '確定撤回',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const headers = { 'X-User-Id': currentUser.value.userId }
      await axios.post(`/api/forms/${selectedForm.value.formId}/withdraw`, {}, { headers })
      ElMessage.success('簽呈已成功撤回')
      detailDialogVisible.value = false
      fetchForms()
    } catch (error) {
      ElMessage.error(error.response?.data || '撤回失敗')
    }
  }).catch(() => {})
}

// 判斷是否為「待我簽核」且主單狀態為 UNDER_REVIEW
const isPendingForMe = computed(() => {
  if (!selectedForm.value || !currentUser.value) return false
  if (selectedForm.value.finalStatus !== 'UNDER_REVIEW') return false
  
  // 檢查在 routes 中，是否有我作為 approver 且狀態為 PENDING
  return selectedForm.value.routes.some(r => 
    r.approver.userId === currentUser.value.userId && r.routeStatus === 'PENDING'
  )
})

// 判斷是否可以抽單 (我是申請人，狀態為 UNDER_REVIEW 且沒有其他主管簽過)
const canWithdraw = computed(() => {
  if (!selectedForm.value || !currentUser.value) return false
  if (selectedForm.value.applicant.userId !== currentUser.value.userId) return false
  if (selectedForm.value.finalStatus !== 'UNDER_REVIEW') return false
  
  // 除了 Step 1 之外，所有主管節點狀態必須都是 PENDING 或 WAITING
  return !selectedForm.value.routes.some(r => 
    r.stepNumber > 1 && (r.routeStatus === 'APPROVED' || r.routeStatus === 'REJECTED')
  )
})

// 下載附件
const downloadFile = async (attachment) => {
  try {
    const response = await axios.get(`/api/attachments/download/${attachment.attachmentId}`, {
      headers: { 'X-User-Id': currentUser.value.userId },
      responseType: 'blob'
    })
    
    const blob = new Blob([response.data], { type: response.headers['content-type'] })
    const link = document.createElement('a')
    link.href = window.URL.createObjectURL(blob)
    link.download = attachment.fileName
    link.click()
    window.URL.revokeObjectURL(link.href)
    ElMessage.success('檔案下載成功')
  } catch (error) {
    ElMessage.error('檔案下載失敗，您可能無此檔案存取權限')
  }
}
</script>

<template>
  <div class="dashboard-wrapper">
    <div class="welcome-banner animate-fade-in-up">
      <h2 class="welcome-title text-gradient">電子簽核中心</h2>
      <p class="welcome-text">歡迎回到系統。您可以在此審批主管事項，或查詢您發起的請假、加班、請款狀態。</p>
    </div>

    <!-- 首頁三大頁籤 -->
    <el-tabs v-model="activeTab" class="dashboard-tabs animate-fade-in-up" @tab-change="fetchForms">
      <!-- 1. 待簽核清單 -->
      <el-tab-pane label="待簽核事項" name="pending">
        <el-table :data="pendingList" style="width: 100%" v-loading="!pendingList" empty-text="目前無待簽核事項">
          <el-table-column prop="formId" label="單號" width="90" align="center" />
          <el-table-column label="簽呈類型" width="140">
            <template #default="scope">
              <el-tag :type="getFormTypeTag(scope.row.formType)" effect="light">
                {{ getFormTypeLabel(scope.row.formType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="簽呈主旨" min-width="200" show-overflow-tooltip />
          <el-table-column prop="applicant.realName" label="申請人" width="120" />
          <el-table-column prop="applicant.department.deptName" label="部門" width="130" />
          <el-table-column label="送出時間" width="170">
            <template #default="scope">
              {{ formatDate(scope.row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center" fixed="right">
            <template #default="scope">
              <el-button type="primary" size="small" @click="showDetail(scope.row.formId)">
                處理
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 2. 已簽核紀錄 -->
      <el-tab-pane label="已簽核紀錄" name="reviewed">
        <el-table :data="reviewedList" style="width: 100%" empty-text="無已簽核歷史紀錄">
          <el-table-column prop="formId" label="單號" width="90" align="center" />
          <el-table-column label="簽呈類型" width="140">
            <template #default="scope">
              <el-tag :type="getFormTypeTag(scope.row.formType)" effect="light">
                {{ getFormTypeLabel(scope.row.formType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="簽呈主旨" min-width="200" show-overflow-tooltip />
          <el-table-column prop="applicant.realName" label="申請人" width="120" />
          <el-table-column label="送出時間" width="170">
            <template #default="scope">
              {{ formatDate(scope.row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="最終狀態" width="120" align="center">
            <template #default="scope">
              <el-tag :type="getStatusTag(scope.row.finalStatus)" effect="dark">
                {{ getStatusLabel(scope.row.finalStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center" fixed="right">
            <template #default="scope">
              <el-button type="info" plain size="small" @click="showDetail(scope.row.formId)">
                檢視
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 3. 我申請的簽呈 -->
      <el-tab-pane label="我發起的申請" name="my">
        <el-table :data="myList" style="width: 100%" empty-text="您尚未發起任何簽呈">
          <el-table-column prop="formId" label="單號" width="90" align="center" />
          <el-table-column label="簽呈類型" width="140">
            <template #default="scope">
              <el-tag :type="getFormTypeTag(scope.row.formType)" effect="light">
                {{ getFormTypeLabel(scope.row.formType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="簽呈主旨" min-width="200" show-overflow-tooltip />
          <el-table-column label="送出時間" width="170">
            <template #default="scope">
              {{ formatDate(scope.row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column label="當前狀態" width="120" align="center">
            <template #default="scope">
              <el-tag :type="getStatusTag(scope.row.finalStatus)" effect="dark">
                {{ getStatusLabel(scope.row.finalStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" align="center" fixed="right">
            <template #default="scope">
              <el-button type="info" plain size="small" @click="showDetail(scope.row.formId)">
                詳情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 簽呈詳細資料 Modal -->
    <el-dialog 
      v-model="detailDialogVisible" 
      title="簽呈單詳細審批資訊" 
      width="780px"
      align-center
      destroy-on-close
    >
      <div v-if="selectedForm" class="dialog-inner">
        <!-- 表單基本標籤 -->
        <div class="form-section-title">基本資訊</div>
        <div class="meta-grid">
          <div class="meta-item"><span class="meta-label">申請單號：</span>{{ selectedForm.formId }}</div>
          <div class="meta-item"><span class="meta-label">申請人：</span>{{ selectedForm.applicant.realName }} ({{ selectedForm.applicant.userId }})</div>
          <div class="meta-item"><span class="meta-label">部門 / 職位：</span>{{ selectedForm.applicant.department.deptName }} / {{ selectedForm.applicant.position }}</div>
          <div class="meta-item">
            <span class="meta-label">表單類型：</span>
            <el-tag :type="getFormTypeTag(selectedForm.formType)">{{ getFormTypeLabel(selectedForm.formType) }}</el-tag>
          </div>
          <div class="meta-item"><span class="meta-label">送出時間：</span>{{ formatDate(selectedForm.createdAt) }}</div>
          <div class="meta-item">
            <span class="meta-label">最終狀態：</span>
            <el-tag :type="getStatusTag(selectedForm.finalStatus)" effect="dark">{{ getStatusLabel(selectedForm.finalStatus) }}</el-tag>
          </div>
        </div>

        <el-divider />

        <!-- 簽呈細節內容 -->
        <div class="form-section-title">簽呈內容</div>
        <div class="content-box">
          <div class="content-row">
            <span class="content-label">簽呈主旨：</span>
            <span class="content-value bold">{{ selectedForm.title }}</span>
          </div>

          <!-- 財務請款特有欄位 -->
          <template v-if="selectedForm.formType === 'ADVANCE' || selectedForm.formType === 'PAYMENT'">
            <div class="content-row">
              <span class="content-label">金額：</span>
              <span class="content-value money">NT$ {{ selectedForm.amount.toLocaleString(undefined, {minimumFractionDigits: 2}) }}</span>
            </div>
            <div v-if="selectedForm.formType === 'PAYMENT'" class="content-row">
              <span class="content-label">發票號碼：</span>
              <span class="content-value">{{ selectedForm.invoiceNum }}</span>
            </div>
            <div v-if="selectedForm.formType === 'PAYMENT'" class="content-row">
              <span class="content-label">發票日期：</span>
              <span class="content-value">{{ formatSimpleDate(selectedForm.invoiceDate) }}</span>
            </div>
          </template>

          <!-- 差勤請假特有欄位 -->
          <template v-if="selectedForm.formType === 'LEAVE' || selectedForm.formType === 'OVERTIME'">
            <div class="content-row">
              <span class="content-label">起迄時間：</span>
              <span class="content-value">{{ formatDate(selectedForm.startTime) }} 至 {{ formatDate(selectedForm.endTime) }}</span>
            </div>
            <div class="content-row">
              <span class="content-label">總時數：</span>
              <span class="content-value bold">{{ selectedForm.totalHours }} 小時</span>
            </div>
          </template>

          <div class="content-row stack">
            <span class="content-label">事由說明：</span>
            <div class="reason-text">{{ selectedForm.reason }}</div>
          </div>

          <!-- 佐證檔案 -->
          <div class="content-row stack" v-if="selectedForm.attachments && selectedForm.attachments.length > 0">
            <span class="content-label">佐證文件 (複數)：</span>
            <div class="attachment-list">
              <div v-for="att in selectedForm.attachments" :key="att.attachmentId" class="attachment-item">
                <el-icon><Document /></el-icon>
                <span class="file-name">{{ att.fileName }} ({{ (att.fileSize/1024).toFixed(1) }} KB)</span>
                <el-button type="primary" link size="small" @click="downloadFile(att)">下載檔案</el-button>
              </div>
            </div>
          </div>
        </div>

        <el-divider />

        <!-- 簽核流轉明細 -->
        <div class="form-section-title">簽核歷程 (Workflow Timeline)</div>
        <div class="timeline-container">
          <el-timeline>
            <el-timeline-item
              v-for="route in selectedForm.routes"
              :key="route.routeId"
              :type="getRouteStatusTag(route.routeStatus)"
              size="large"
              :hollow="route.routeStatus === 'WAITING'"
            >
              <div class="timeline-node">
                <div class="node-header">
                  <span class="node-title">
                    第 {{ route.stepNumber }} 關 - 
                    <span v-if="route.stepNumber === 1">申請人</span>
                    <span v-else>審核主管</span>
                    : {{ route.approver.realName }} ({{ route.approver.userId }})
                  </span>
                  <el-tag :type="getRouteStatusTag(route.routeStatus)" size="small">
                    {{ getRouteStatusLabel(route.routeStatus) }}
                  </el-tag>
                </div>
                <div class="node-body">
                  <div class="node-comment" v-if="route.approverComment">
                    <span class="comment-label">簽核意見：</span>{{ route.approverComment }}
                  </div>
                  <div class="node-time" v-if="route.reviewedAt">
                    簽核時間：{{ formatDate(route.reviewedAt) }}
                  </div>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>

        <!-- 主管審批操作區 -->
        <div v-if="isPendingForMe" class="approval-action-box glass-panel">
          <div class="action-title">主管審核決策</div>
          <el-input
            v-model="comment"
            type="textarea"
            :rows="3"
            placeholder="請輸入審核意見 (同意時選填，駁回時必填)"
            maxlength="200"
            show-word-limit
          />
          <div class="action-buttons">
            <el-button type="danger" :loading="submitLoading" @click="handleReject">駁回 (REJECT)</el-button>
            <el-button type="success" :loading="submitLoading" @click="handleApprove">同意 (APPROVE)</el-button>
          </div>
        </div>

        <!-- 申請人抽單區 -->
        <div v-if="canWithdraw" class="withdraw-action-box">
          <el-button type="danger" plain style="width: 100%" @click="handleWithdraw">
            撤回（抽單）此申請單並作廢
          </el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.dashboard-wrapper {
  padding-top: 10px;
}

.welcome-banner {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 24px;
  margin-bottom: 24px;
  box-shadow: var(--shadow-sm);
}

.welcome-title {
  font-size: 22px;
  font-weight: 800;
  margin-bottom: 6px;
}

.welcome-text {
  font-size: 14px;
  color: var(--text-muted);
}

.dashboard-tabs {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

:deep(.el-tabs__nav-wrap::after) {
  background-color: var(--border-color) !important;
}

.dialog-inner {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.form-section-title {
  font-family: var(--font-title);
  font-size: 15px;
  font-weight: 700;
  color: var(--primary);
  border-left: 4px solid var(--primary);
  padding-left: 8px;
  margin-bottom: 8px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  background-color: #f8fafc;
  padding: 16px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
  font-size: 13px;
}

.meta-label {
  color: var(--text-muted);
  font-weight: 500;
}

.content-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background-color: #f8fafc;
  padding: 16px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
  font-size: 14px;
}

.content-row {
  display: flex;
  align-items: center;
}

.content-row.stack {
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}

.content-label {
  width: 100px;
  color: var(--text-muted);
  font-weight: 500;
}

.content-row.stack .content-label {
  width: auto;
}

.content-value {
  color: var(--text-main);
}

.content-value.bold {
  font-weight: 600;
}

.content-value.money {
  font-family: var(--font-title);
  color: #ef4444;
  font-size: 16px;
  font-weight: 700;
}

.reason-text {
  width: 100%;
  background-color: #ffffff;
  border: 1px solid var(--border-color);
  padding: 12px;
  border-radius: var(--radius-sm);
  color: var(--text-main);
  white-space: pre-wrap;
  min-height: 60px;
  font-size: 13px;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 8px;
  background-color: #ffffff;
  border: 1px solid var(--border-color);
  padding: 6px 12px;
  border-radius: var(--radius-sm);
  font-size: 13px;
}

.file-name {
  flex: 1;
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline-container {
  padding: 10px 20px;
}

.timeline-node {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.node-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.node-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main);
}

.node-body {
  font-size: 13px;
  color: var(--text-muted);
  background-color: #f8fafc;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  margin-top: 4px;
}

.node-comment {
  color: var(--text-main);
}

.comment-label {
  font-weight: 500;
  color: var(--text-muted);
}

.node-time {
  font-size: 11px;
  color: var(--text-light);
  margin-top: 4px;
}

.approval-action-box {
  background-color: #fffbeb;
  border: 1px solid #fef3c7;
  padding: 20px;
  margin-top: 10px;
}

.action-title {
  font-family: var(--font-title);
  font-size: 15px;
  font-weight: 700;
  color: #b45309;
  margin-bottom: 12px;
}

.action-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
}

.withdraw-action-box {
  margin-top: 10px;
}
</style>
