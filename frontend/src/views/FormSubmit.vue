<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()
const currentUser = ref(null)
const departments = ref([])
const allUsers = ref([])
const fileList = ref([])
const submitLoading = ref(false)

// 人員選擇 Dialog 狀態
const employeeDialogVisible = ref(false)
const selectedDeptId = ref('')
const searchKeyword = ref('')
const selectedEmployees = ref([]) // 勾選的員工 userIds

const form = reactive({
  title: '',
  formType: 'LEAVE',
  amount: null,
  invoiceNum: '',
  invoiceDate: '',
  startTime: '',
  endTime: '',
  reason: ''
})

const routes = ref([]) // 簽核節點清單

const validateInvoiceNum = (rule, value, callback) => {
  if (form.formType === 'PAYMENT') {
    if (!value) {
      return callback(new Error('請輸入發票號碼'))
    }
    const invoiceRegex = /^[A-Z]{2}\d{8}$/
    if (!invoiceRegex.test(value)) {
      return callback(new Error('發票號碼格式不正確，應為 2 碼大寫英文與 8 碼數字（例如：AB12345678）'))
    }
  }
  callback()
}

const validateInvoiceDate = (rule, value, callback) => {
  if (form.formType === 'PAYMENT') {
    if (!value) {
      return callback(new Error('請選擇發票日期'))
    }
    const invDate = new Date(value)
    const tomorrow = new Date()
    tomorrow.setHours(24, 0, 0, 0)
    if (invDate >= tomorrow) {
      return callback(new Error('發票日期不可為未來日期'))
    }
  }
  callback()
}

const validateStartTime = (rule, value, callback) => {
  if (form.formType === 'LEAVE' || form.formType === 'OVERTIME') {
    if (!value) {
      return callback(new Error('請選擇開始時間'))
    }
  }
  callback()
}

const validateEndTime = (rule, value, callback) => {
  if (form.formType === 'LEAVE' || form.formType === 'OVERTIME') {
    if (!value) {
      return callback(new Error('請選擇結束時間'))
    }
    if (form.startTime && new Date(form.startTime) >= new Date(value)) {
      return callback(new Error('結束時間必須大於開始時間'))
    }
    if (form.formType === 'OVERTIME') {
      if (new Date(value) > new Date()) {
        return callback(new Error('加班結束時間不可為未來時間'))
      }
    }
  }
  callback()
}

const formRules = {
  title: [{ required: true, message: '請輸入簽呈主旨', trigger: 'blur' }],
  formType: [{ required: true, message: '請選擇表單類型', trigger: 'change' }],
  amount: [
    {
      validator: (rule, value, callback) => {
        if (form.formType === 'ADVANCE' || form.formType === 'PAYMENT') {
          if (value === null || value === undefined || value <= 0) {
            return callback(new Error('請款金額必須大於 0'))
          }
          if (value > 999999999.99) {
            return callback(new Error('請款金額超出限制'))
          }
        }
        callback()
      },
      trigger: ['blur', 'change']
    }
  ],
  invoiceNum: [{ validator: validateInvoiceNum, trigger: ['blur', 'change'] }],
  invoiceDate: [{ validator: validateInvoiceDate, trigger: ['blur', 'change'] }],
  startTime: [{ validator: validateStartTime, trigger: ['blur', 'change'] }],
  endTime: [{ validator: validateEndTime, trigger: ['blur', 'change'] }],
  reason: [{ required: true, message: '請輸入用途說明或事由', trigger: 'blur' }]
}

const formRef = ref(null)

const loadData = async () => {
  const user = localStorage.getItem('currentUser')
  if (!user) {
    router.push('/login')
    return
  }
  currentUser.value = JSON.parse(user)

  // 取得部門與員工資料以供流程配置
  try {
    const [deptRes, userRes] = await Promise.all([
      axios.get('/api/users/departments'),
      axios.get('/api/users')
    ])
    departments.value = deptRes.data
    allUsers.value = userRes.data

    // 初始化簽核節點：
    // Step 1: 申請人自己
    routes.value = [
      {
        approver: {
          userId: currentUser.value.userId,
          realName: currentUser.value.realName
        },
        stepNumber: 1,
        subStep: 1,
        type: 'APPLICANT',
        isDefault: true // 申請人自己
      }
    ]

    // Step 2: 預設直屬主管
    if (currentUser.value.manager_id) {
      const managerUser = allUsers.value.find(u => u.userId === currentUser.value.manager_id)
      if (managerUser) {
        routes.value.push({
          approver: {
            userId: managerUser.userId,
            realName: managerUser.realName
          },
          stepNumber: 2,
          subStep: 1,
          type: 'MANAGER',
          isDefault: true // 直屬主管
        })
      }
    } else {
      // 找不到主管時嘗試向後端獲取或直接由 users 中的 manager 關聯尋找 (有些人在 Users 中 manager 為 LAZY)
      const fullMe = allUsers.value.find(u => u.userId === currentUser.value.userId)
      if (fullMe && fullMe.manager) {
        routes.value.push({
          approver: {
            userId: fullMe.manager.userId,
            realName: fullMe.manager.realName
          },
          stepNumber: 2,
          subStep: 1,
          type: 'MANAGER',
          isDefault: true
        })
      }
    }
  } catch (error) {
    ElMessage.error('載入基礎資料失敗')
  }
}

onMounted(() => {
  loadData()
})

// 選擇時間以半小時為區間：Element Plus datetime-picker 支援 step 設定
// 前端 UI 限制以「半小時」為一區間選取

// 篩選彈窗中的員工列表 (前端即時過濾 Client-side Filtering)
const filteredUsersForSelect = computed(() => {
  return allUsers.value.filter(user => {
    // 排除已經在簽核路徑中的人與自己，防止重複配置
    const isAlreadyIn = routes.value.some(r => r.approver.userId === user.userId)
    if (isAlreadyIn) return false

    // 部門過濾
    if (selectedDeptId.value && user.department.deptId !== selectedDeptId.value) {
      return false
    }

    // 關鍵字搜尋（員工編號或真實姓名）
    if (searchKeyword.value.trim()) {
      const keyword = searchKeyword.value.toLowerCase()
      const matchId = user.userId.toLowerCase().includes(keyword)
      const matchName = user.realName.toLowerCase().includes(keyword)
      return matchId || matchName
    }

    return true
  })
})

const openAddApprover = () => {
  selectedEmployees.value = []
  searchKeyword.value = ''
  selectedDeptId.value = ''
  employeeDialogVisible.value = true
}

// 加入會辦：複選之人員會被歸在同一個 step_number，但 sub_step 不同
const addAsJointSign = () => {
  if (selectedEmployees.value.length === 0) {
    ElMessage.warning('請至少選擇一位員工')
    return
  }

  // 取得目前最大的 stepNumber (若只有 step 1 則最大是 1)
  const maxStep = Math.max(...routes.value.map(r => r.stepNumber))
  const targetStep = maxStep + 1

  selectedEmployees.value.forEach((userId, index) => {
    const user = allUsers.value.find(u => u.userId === userId)
    if (user) {
      routes.value.push({
        approver: {
          userId: user.userId,
          realName: user.realName
        },
        stepNumber: targetStep,
        subStep: index + 1,
        type: 'JOINT' // 會辦
      })
    }
  })

  employeeDialogVisible.value = false
  ElMessage.success('成功加入會辦節點')
}

// 加入串簽：複選之人員會依序遞增 step_number (每個人的 subStep 均為 1)
const addAsSequentialSign = () => {
  if (selectedEmployees.value.length === 0) {
    ElMessage.warning('請至少選擇一位員工')
    return
  }

  let maxStep = Math.max(...routes.value.map(r => r.stepNumber))

  selectedEmployees.value.forEach(userId => {
    const user = allUsers.value.find(u => u.userId === userId)
    if (user) {
      maxStep++
      routes.value.push({
        approver: {
          userId: user.userId,
          realName: user.realName
        },
        stepNumber: maxStep,
        subStep: 1,
        type: 'SEQUENTIAL' // 串簽
      })
    }
  })

  employeeDialogVisible.value = false
  ElMessage.success('成功加入串簽節點')
}

// 刪除節點並重算 step_number (重新編排)
const removeRouteNode = (index) => {
  routes.value.splice(index, 1)
  rebuildSteps()
}

// 重新編排 step_number 往前遞補演算法
const rebuildSteps = () => {
  const step1 = routes.value.filter(r => r.stepNumber === 1)
  const others = routes.value.filter(r => r.stepNumber > 1)

  // 排序
  others.sort((a, b) => {
    if (a.stepNumber !== b.stepNumber) {
      return a.stepNumber - b.stepNumber
    }
    return a.subStep - b.subStep
  })

  const newRoutes = [...step1]
  let currentOldStep = -1
  let newStepNum = 1
  let currentSubStep = 1

  for (const route of others) {
    if (route.stepNumber !== currentOldStep) {
      currentOldStep = route.stepNumber
      newStepNum++
      currentSubStep = 1
    } else {
      currentSubStep++
    }

    route.stepNumber = newStepNum
    route.subStep = currentSubStep
    newRoutes.push(route)
  }

  routes.value = newRoutes
}

const disabledInvoiceDate = (time) => {
  return time.getTime() > Date.now()
}

const disabledOvertimeDate = (time) => {
  return time.getTime() > Date.now()
}

const reasonLabel = computed(() => {
  if (form.formType === 'LEAVE') return '請假事由'
  if (form.formType === 'OVERTIME') return '加班內容'
  if (form.formType === 'ADVANCE') return '預支用途'
  if (form.formType === 'PAYMENT') return '墊付內容'
  return '說明'
})

const reasonPlaceholder = computed(() => {
  return `請輸入${reasonLabel.value}`
})

const handleFileChange = (file, fileListBack) => {
  fileList.value = fileListBack
}

const handleFileRemove = (file, fileListBack) => {
  fileList.value = fileListBack
}

const handleExceed = () => {
  ElMessage.warning('上傳附件檔案大小上限限制為 2MB，請先檢查檔案大小')
}

// 發送表單
const submitForm = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      // 驗證流程節點：必須至少包含自己(1)與一個審核主管(2)
      if (routes.value.length < 2) {
        ElMessage.warning('簽核流程不完整，必須至少配置直屬主管或其他審核人')
        return
      }

      submitLoading.value = true

      // 組裝 Multipart Form Data
      const formData = new FormData()

      // 表單 JSON
      const formPayload = {
        title: form.title,
        formType: form.formType,
        amount: form.amount,
        invoiceNum: form.invoiceNum,
        invoiceDate: form.invoiceDate || null,
        startTime: form.startTime || null,
        endTime: form.endTime || null,
        reason: form.reason
      }
      formData.append('form', JSON.stringify(formPayload))

      // 節點 JSON
      const routesPayload = routes.value.map(r => ({
        approver: { userId: r.approver.userId },
        stepNumber: r.stepNumber,
        subStep: r.subStep,
        routeStatus: r.stepNumber === 1 ? 'APPROVED' : (r.stepNumber === 2 ? 'PENDING' : 'WAITING')
      }))
      formData.append('routes', JSON.stringify(routesPayload))

      // 附件檔案 (檢查 2MB)
      let filesTooLarge = false
      fileList.value.forEach(f => {
        if (f.raw.size > 2 * 1024 * 1024) {
          filesTooLarge = true
        }
        formData.append('files', f.raw)
      })

      if (filesTooLarge) {
        ElMessage.error('上傳失敗：檔案中包含超過 2MB 的檔案')
        submitLoading.value = false
        return
      }

      try {
        const headers = {
          'X-User-Id': currentUser.value.userId,
          'Content-Type': 'multipart/form-data'
        }

        await axios.post('/api/forms', formData, { headers })
        ElMessage.success('簽呈單發起成功！')
        router.push('/')
      } catch (error) {
        ElMessage.error(error.response?.data || '發起簽呈失敗')
      } finally {
        submitLoading.value = false
      }
    }
  })
}
</script>

<template>
  <div class="submit-wrapper animate-fade-in">
    <el-card class="form-card">
      <template #header>
        <div class="card-header">
          <span class="card-title text-gradient">發起新簽呈單</span>
          <span class="card-subtitle">填寫表單內容並配置審批工作流</span>
        </div>
      </template>

      <!-- 申請人唯讀資料 -->
      <div class="applicant-info-row" v-if="currentUser">
        <div class="info-item"><span class="label">申請人員：</span>{{ currentUser.realName }} ({{ currentUser.userId }})</div>
        <div class="info-item"><span class="label">所屬部門：</span>{{ currentUser.department?.deptName }}</div>
        <div class="info-item"><span class="label">職稱職位：</span>{{ currentUser.position }}</div>
      </div>

      <el-form :model="form" :rules="formRules" ref="formRef" label-position="top">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="簽呈表單類型" prop="formType">
              <el-select v-model="form.formType" placeholder="選擇表單類型" style="width: 100%">
                <el-option label="請假申請單" value="LEAVE" />
                <el-option label="加班申請單" value="OVERTIME" />
                <el-option label="預支請款單" value="ADVANCE" />
                <el-option label="墊付請款單" value="PAYMENT" />
              </el-select>
              <div v-if="form.formType === 'PAYMENT'" class="form-tip" style="color: #e6a23c; font-size: 13px; margin-top: 6px; font-weight: 500;">
                ⚠️ 備註：一張發票請獨立申請一份簽呈
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="簽呈主旨" prop="title">
              <el-input v-model="form.title" placeholder="請輸入主旨" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- A. 請假特有欄位 -->
        <template v-if="form.formType === 'LEAVE'">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="請假開始時間 (以半小時為區間)" prop="startTime">
                <el-date-picker
                  v-model="form.startTime"
                  type="datetime"
                  placeholder="選擇開始時間"
                  format="YYYY-MM-DD HH:mm"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  style="width: 100%"
                  :time-select-options="{ step: '00:30' }"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="請假結束時間 (以半小時為區間)" prop="endTime">
                <el-date-picker
                  v-model="form.endTime"
                  type="datetime"
                  placeholder="選擇結束時間"
                  format="YYYY-MM-DD HH:mm"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  style="width: 100%"
                  :time-select-options="{ step: '00:30' }"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <!-- B. 加班特有欄位 -->
        <template v-if="form.formType === 'OVERTIME'">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="加班開始時間 (以半小時為區間)" prop="startTime">
                <el-date-picker
                  v-model="form.startTime"
                  type="datetime"
                  placeholder="選擇加班開始時間"
                  format="YYYY-MM-DD HH:mm"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  style="width: 100%"
                  :disabled-date="disabledOvertimeDate"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="加班結束時間 (以半小時為區間)" prop="endTime">
                <el-date-picker
                  v-model="form.endTime"
                  type="datetime"
                  placeholder="選擇加班結束時間"
                  format="YYYY-MM-DD HH:mm"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  style="width: 100%"
                  :disabled-date="disabledOvertimeDate"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <div class="form-tip" style="color: #e6a23c; font-size: 13px; margin-top: -6px; margin-bottom: 12px; font-weight: 500;">
            ⚠️ 提示：平日正常上班時間 (08:00 - 17:00) 將自動扣除；假日 (週六日) 加班自動扣除中午休息 (12:00 - 13:00)。
          </div>
        </template>

        <!-- C. 預支請款特有欄位 -->
        <template v-if="form.formType === 'ADVANCE'">
          <el-row :gutter="20">
            <el-col :span="24">
              <el-form-item label="預支請款金額 (大於零)" prop="amount">
                <el-input-number 
                  v-model="form.amount" 
                  :precision="2" 
                  :step="100" 
                  :min="0.01" 
                  style="width: 100%" 
                  placeholder="請輸入預支金額"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <!-- D. 墊付請款特有欄位 (一單一發票) -->
        <template v-if="form.formType === 'PAYMENT'">
          <el-row :gutter="20">
            <el-col :span="8">
              <el-form-item label="發票號碼" prop="invoiceNum">
                <el-input v-model="form.invoiceNum" placeholder="請輸入發票號碼" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="發票日期" prop="invoiceDate">
                <el-date-picker
                  v-model="form.invoiceDate"
                  type="date"
                  placeholder="選擇發票日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                  :disabled-date="disabledInvoiceDate"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="發票金額 (大於零)" prop="amount">
                <el-input-number 
                  v-model="form.amount" 
                  :precision="2" 
                  :step="100" 
                  :min="0.01" 
                  style="width: 100%" 
                  placeholder="請輸入發票金額"
                />
              </el-form-item>
            </el-col>
          </el-row>
        </template>

        <el-form-item :label="reasonLabel" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" :placeholder="reasonPlaceholder" />
        </el-form-item>

        <!-- 檔案上傳區 (僅預支與墊付請款單顯示) -->
        <el-form-item 
          v-if="form.formType === 'ADVANCE' || form.formType === 'PAYMENT'" 
          label="上傳檔案 (限制每個檔案最大 2MB，可複選上傳)"
        >
          <el-upload
            action="#"
            :auto-upload="false"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            :file-list="fileList"
            multiple
            :limit="5"
            :on-exceed="handleExceed"
          >
            <el-button type="info" plain size="small">
              <el-icon><Upload /></el-icon> 選擇檔案
            </el-button>
          </el-upload>
        </el-form-item>

        <el-divider />

        <!-- 工作流配置面板 -->
        <div class="workflow-section">
          <div class="workflow-header">
            <h3 class="section-title">簽核順序</h3>
            <el-button type="primary" size="small" @click="openAddApprover" style="color: white; font-weight: bold;">
              <el-icon><Plus /></el-icon> 新增簽核順序
            </el-button>
          </div>

          <div class="route-timeline">
            <el-table :data="routes" style="width: 100%" border class="workflow-table">
              <el-table-column label="順序" width="100" align="center">
                <template #default="scope">
                  <span class="step-text">0{{ scope.row.stepNumber }}</span>
                </template>
              </el-table-column>
              
              <el-table-column label="簽核方式" width="160" align="center">
                <template #default="scope">
                  <el-tag v-if="scope.row.stepNumber === 1" type="info" effect="plain">發起人</el-tag>
                  <el-tag v-else-if="scope.row.stepNumber === 2 && scope.row.type === 'MANAGER'" type="warning" effect="plain">直屬主管</el-tag>
                  <el-tag v-else-if="scope.row.type === 'JOINT' || routes.filter(r => r.stepNumber === scope.row.stepNumber).length > 1" type="danger" effect="plain">會辦關卡</el-tag>
                  <el-tag v-else type="primary" effect="plain">串簽關卡</el-tag>
                </template>
              </el-table-column>

              <el-table-column label="簽核成員">
                <template #default="scope">
                  <div class="approver-cell">
                    <span class="approver-name">{{ scope.row.approver.realName }}</span>
                    <span class="approver-id">({{ scope.row.approver.userId }})</span>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="操作" width="120" align="center">
                <template #default="scope">
                  <!-- 第一關自己與第二關預設主管不開放刪除 -->
                  <span v-if="scope.row.isDefault" class="lock-text">系統鎖定</span>
                  <el-button 
                    v-else 
                    type="danger" 
                    icon="Delete" 
                    circle 
                    size="small"
                    @click="removeRouteNode(scope.$index)"
                  />
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <el-divider />

        <div class="submit-action">
          <el-button type="primary" size="large" :loading="submitLoading" @click="submitForm">
            發起並送出簽呈
          </el-button>
        </div>
      </el-form>
    </el-card>

    <!-- 員工選擇彈窗 (Client-side 即時過濾) -->
    <el-dialog v-model="employeeDialogVisible" title="選擇審核人員" width="600px" align-center>
      <div class="dialog-selector">
        <el-row :gutter="20">
          <!-- 左側部門過濾 -->
          <el-col :span="8" class="dept-col">
            <div class="selector-title">部門篩選</div>
            <div class="dept-list">
              <div 
                class="dept-item" 
                :class="{ active: selectedDeptId === '' }"
                @click="selectedDeptId = ''"
              >
                所有部門
              </div>
              <div 
                v-for="dept in departments" 
                :key="dept.deptId" 
                class="dept-item"
                :class="{ active: selectedDeptId === dept.deptId }"
                @click="selectedDeptId = dept.deptId"
              >
                {{ dept.deptName }}
              </div>
            </div>
          </el-col>

          <!-- 右側員工選擇 (支援搜尋、複選) -->
          <el-col :span="16" class="user-col">
            <div class="selector-title">選擇員工 (可複選)</div>
            <el-input
              v-model="searchKeyword"
              placeholder="搜尋編號或姓名..."
              prefix-icon="Search"
              clearable
              style="margin-bottom: 12px"
            />
            
            <div class="user-checkbox-group">
              <el-checkbox-group v-model="selectedEmployees">
                <div 
                  v-for="user in filteredUsersForSelect" 
                  :key="user.userId"
                  class="user-check-item"
                >
                  <el-checkbox :value="user.userId">
                    {{ user.realName }} ({{ user.userId }}) - {{ user.position }}
                  </el-checkbox>
                </div>
              </el-checkbox-group>
              <div v-if="filteredUsersForSelect.length === 0" class="empty-users">
                無可選的員工或已被加入路徑
              </div>
            </div>
          </el-col>
        </el-row>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="employeeDialogVisible = false">取消</el-button>
          <el-button type="warning" @click="addAsJointSign">加入會辦 (同關卡)</el-button>
          <el-button type="primary" @click="addAsSequentialSign">加入串簽 (依序推進)</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.submit-wrapper {
  max-width: 900px;
  margin: 0 auto;
  padding-top: 10px;
}

.form-card {
  box-shadow: var(--shadow-md);
  border-radius: var(--radius-md);
}

.card-header {
  display: flex;
  flex-direction: column;
}

.card-title {
  font-size: 20px;
  font-weight: 800;
}

.card-subtitle {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 4px;
}

.applicant-info-row {
  display: flex;
  gap: 30px;
  background-color: #f1f5f9;
  padding: 14px 20px;
  border-radius: var(--radius-sm);
  margin-bottom: 24px;
  font-size: 13px;
  border: 1px solid var(--border-color);
}

.applicant-info-row .label {
  color: var(--text-muted);
  font-weight: 500;
}

.applicant-info-row .info-item {
  color: var(--text-main);
  font-weight: 600;
}

.workflow-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.workflow-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  font-family: var(--font-title);
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}

.route-timeline {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.route-node-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background-color: #ffffff;
}

.node-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.step-badge {
  background-color: var(--primary-light);
  color: var(--primary);
  font-family: var(--font-title);
  font-weight: 700;
  font-size: 13px;
  padding: 4px 10px;
  border-radius: var(--radius-full);
}

.node-name {
  font-weight: 600;
  color: var(--text-main);
}

.node-id {
  color: var(--text-muted);
  font-size: 13px;
  margin-left: 4px;
}

.node-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.submit-action {
  display: flex;
  justify-content: center;
  padding-top: 10px;
}

.submit-action .el-button {
  padding: 24px 40px !important;
  font-size: 16px;
  font-weight: 600;
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.3);
}

/* 彈窗人員選擇器 */
.dialog-selector {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  overflow: hidden;
  height: 380px;
}

.selector-title {
  font-family: var(--font-title);
  font-size: 13px;
  font-weight: 600;
  background-color: #f1f5f9;
  padding: 8px 12px;
  border-bottom: 1px solid var(--border-color);
  color: var(--text-main);
}

.dept-col {
  border-right: 1px solid var(--border-color);
  height: 100%;
  padding: 0 !important;
  display: flex;
  flex-direction: column;
}

.user-col {
  height: 100%;
  padding: 0 !important;
  display: flex;
  flex-direction: column;
}

.dept-list {
  flex: 1;
  overflow-y: auto;
}

.dept-item {
  padding: 10px 14px;
  font-size: 13px;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.dept-item:hover {
  background-color: #f8fafc;
  color: var(--primary);
}

.dept-item.active {
  background-color: var(--primary-light);
  color: var(--primary);
  font-weight: 600;
  border-left: 4px solid var(--primary);
}

.user-col .el-input {
  padding: 12px 12px 0 12px;
}

.user-checkbox-group {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px 12px 12px;
}

.user-check-item {
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
}

.empty-users {
  text-align: center;
  font-size: 12px;
  color: var(--text-light);
  margin-top: 40px;
}

/* 簽核順序表格化樣式 */
.workflow-table {
  border-radius: var(--radius-sm);
  overflow: hidden;
  margin-top: 10px;
}

.step-text {
  font-family: var(--font-title);
  font-weight: 700;
  font-size: 14px;
  color: var(--primary);
}

.approver-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-left: 8px;
}

.approver-name {
  font-weight: 600;
  color: var(--text-main);
}

.approver-id {
  color: var(--text-muted);
  font-size: 12px;
}

.lock-text {
  font-size: 12px;
  color: var(--text-light);
  font-weight: 500;
}

:deep(.el-form-item__error) {
  position: relative;
  top: auto;
  left: auto;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
