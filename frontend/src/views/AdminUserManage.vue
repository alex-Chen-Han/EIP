<script setup>
import { ref, onMounted, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const currentUser = ref(null)
const users = ref([])
const departments = ref([])

// Dialog 控制
const userDialogVisible = ref(false)
const pwdDialogVisible = ref(false)
const dialogType = ref('add') // 'add' 或 'edit'
const submitLoading = ref(false)

const pwdForm = reactive({
  userId: '',
  newPassword: ''
})

const pwdRules = {
  newPassword: [
    { required: true, message: '請輸入新密碼', trigger: 'blur' },
    { min: 6, message: '密碼長度至少為 6 個字元', trigger: 'blur' }
  ]
}

const userForm = reactive({
  userId: '',
  password: '',
  realName: '',
  idNumber: '',
  bankAccount: '',
  deptId: '',
  position: '',
  role: 'EMPLOYEE',
  managerId: ''
})

const userRules = {
  userId: [{ required: true, message: '請輸入員工編號', trigger: 'blur' }],
  password: [
    { required: true, message: '請輸入登入密碼', trigger: 'blur' },
    { min: 6, message: '密碼長度至少為 6 個字元', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '請輸入員工真實姓名', trigger: 'blur' }],
  idNumber: [
    { required: true, message: '請輸入身份證字號', trigger: 'blur' },
    { len: 10, message: '身份證字號必須為 10 碼', trigger: 'blur' }
  ],
  bankAccount: [{ required: true, message: '請輸入薪轉戶銀行帳號', trigger: 'blur' }],
  deptId: [{ required: true, message: '請選擇所屬部門', trigger: 'change' }],
  position: [{ required: true, message: '請輸入職位名稱', trigger: 'blur' }],
  role: [{ required: true, message: '請選擇角色權限', trigger: 'change' }]
}

const userFormRef = ref(null)
const pwdFormRef = ref(null)

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

  try {
    const headers = { 'X-User-Id': currentUser.value.userId }
    const [userRes, deptRes] = await Promise.all([
      axios.get('/api/users', { headers }),
      axios.get('/api/users/departments', { headers })
    ])
    users.value = userRes.data
    departments.value = deptRes.data
  } catch (error) {
    ElMessage.error('載入資料失敗')
  }
}

onMounted(() => {
  loadData()
})

// 可選主管名單：排除自己以及非 MANAGER/ADMIN 角色（主管通常是 MANAGER）
const potentialManagers = computed(() => {
  return users.value.filter(u => u.role === 'MANAGER' || u.role === 'ADMIN')
})

const openAddUser = () => {
  dialogType.value = 'add'
  // 重設表單
  userForm.userId = ''
  userForm.password = ''
  userForm.realName = ''
  userForm.idNumber = ''
  userForm.bankAccount = ''
  userForm.deptId = ''
  userForm.position = ''
  userForm.role = 'EMPLOYEE'
  userForm.managerId = ''
  
  userDialogVisible.value = true
}

const openEditUser = (row) => {
  dialogType.value = 'edit'
  userForm.userId = row.userId
  userForm.password = 'dummyPassword123' // 編輯時密碼欄位唯讀或不用，只是為了過濾 validation
  userForm.realName = row.realName
  userForm.idNumber = row.idNumber
  userForm.bankAccount = row.bankAccount
  userForm.deptId = row.department.deptId
  userForm.position = row.position
  userForm.role = row.role
  userForm.managerId = row.manager_id || ''
  
  userDialogVisible.value = true
}

const openChangePwd = (row) => {
  pwdForm.userId = row.userId
  pwdForm.newPassword = ''
  pwdDialogVisible.value = true
}

const saveUser = async () => {
  if (!userFormRef.value) return
  
  await userFormRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      const headers = { 'X-User-Id': currentUser.value.userId }
      
      try {
        if (dialogType.value === 'add') {
          await axios.post('/api/users', {
            userId: userForm.userId,
            password: userForm.password,
            realName: userForm.realName,
            idNumber: userForm.idNumber,
            bankAccount: userForm.bankAccount,
            deptId: userForm.deptId,
            position: userForm.position,
            role: userForm.role,
            managerId: userForm.managerId || null
          }, { headers })
          ElMessage.success('員工帳號建立成功')
        } else {
          await axios.put(`/api/users/${userForm.userId}`, {
            realName: userForm.realName,
            idNumber: userForm.idNumber,
            bankAccount: userForm.bankAccount,
            deptId: userForm.deptId,
            position: userForm.position,
            role: userForm.role,
            managerId: userForm.managerId || null
          }, { headers })
          ElMessage.success('員工帳號更新成功')
        }
        
        userDialogVisible.value = false
        loadData()
      } catch (error) {
        ElMessage.error(error.response?.data || '儲存失敗')
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const savePassword = async () => {
  if (!pwdFormRef.value) return
  
  await pwdFormRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      const headers = { 'X-User-Id': currentUser.value.userId }
      
      try {
        await axios.put(`/api/users/${pwdForm.userId}/password`, {
          newPassword: pwdForm.newPassword
        }, { headers })
        
        ElMessage.success('員工密碼變更成功')
        pwdDialogVisible.value = false
      } catch (error) {
        ElMessage.error(error.response?.data || '變更密碼失敗')
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const deleteUser = (row) => {
  if (row.userId === currentUser.value.userId) {
    ElMessage.warning('無法刪除您目前登入的帳號')
    return
  }

  ElMessageBox.confirm(`確定要刪除員工帳號 ${row.realName} (${row.userId}) 嗎？`, '刪除員工', {
    confirmButtonText: '確定刪除',
    cancelButtonText: '取消',
    type: 'danger'
  }).then(async () => {
    try {
      const headers = { 'X-User-Id': currentUser.value.userId }
      await axios.delete(`/api/users/${row.userId}`, { headers })
      ElMessage.success('員工帳號刪除成功')
      loadData()
    } catch (error) {
      ElMessage.error(error.response?.data || '刪除員工失敗')
    }
  }).catch(() => {})
}

const getRoleLabel = (role) => {
  const map = {
    'EMPLOYEE': '普通職員 (EMPLOYEE)',
    'MANAGER': '部門主管 (MANAGER)',
    'ADMIN': '系統管理員 (ADMIN)'
  }
  return map[role] || role
}

const getRoleTag = (role) => {
  const map = {
    'EMPLOYEE': 'info',
    'MANAGER': 'primary',
    'ADMIN': 'danger'
  }
  return map[role] || 'info'
}
</script>

<template>
  <div class="user-manage-wrapper animate-fade-in">
    <div class="admin-header-row">
      <div class="header-left">
        <h2 class="admin-title text-gradient">員工帳號管理</h2>
        <p class="admin-subtitle">管理公司內部的員工帳號、部門關聯、職權角色與直屬主管</p>
      </div>
      <el-button type="primary" icon="Plus" @click="openAddUser">新增員工帳號</el-button>
    </div>

    <!-- 員工帳號列表表格 -->
    <el-card class="table-card">
      <el-table :data="users" style="width: 100%" empty-text="目前系統無任何員工帳號資料">
        <el-table-column prop="userId" label="員工編號" width="110" fixed />
        <el-table-column prop="realName" label="真實姓名" width="110" />
        <el-table-column prop="department.deptName" label="部門名稱" width="120" />
        <el-table-column prop="position" label="職位名稱" width="120" />
        <el-table-column label="權限角色" width="150">
          <template #default="scope">
            <el-tag :type="getRoleTag(scope.row.role)" effect="plain">
              {{ getRoleLabel(scope.row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="idNumber" label="身分證字號" width="120" />
        <el-table-column prop="bankAccount" label="銀行帳號" min-width="150" />
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="scope">
            <el-button type="primary" link size="small" @click="openEditUser(scope.row)">編輯</el-button>
            <el-button type="warning" link size="small" @click="openChangePwd(scope.row)">改密碼</el-button>
            <el-button type="danger" link size="small" @click="deleteUser(scope.row)">刪除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 使用者編輯/新增彈窗 -->
    <el-dialog 
      v-model="userDialogVisible" 
      :title="dialogType === 'add' ? '新增員工帳號' : '編輯員工資料'" 
      width="640px"
      align-center
    >
      <el-form :model="userForm" :rules="userRules" ref="userFormRef" label-position="top">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="員工編號 (登入帳號)" prop="userId" :disabled="dialogType === 'edit'">
              <el-input v-model="userForm.userId" placeholder="例如: EMP001" :disabled="dialogType === 'edit'" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <!-- 僅在新增時顯示密碼欄位 -->
            <el-form-item v-if="dialogType === 'add'" label="密碼" prop="password">
              <el-input v-model="userForm.password" type="password" placeholder="請設定登入密碼" show-password />
            </el-form-item>
            <div v-else class="password-notice">
              <span>密碼設定：</span>
              <el-button type="warning" size="small" @click="pwdDialogVisible = true; pwdForm.userId = userForm.userId; userDialogVisible = false">變更此帳號密碼</el-button>
            </div>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="真實姓名" prop="realName">
              <el-input v-model="userForm.realName" placeholder="例如: 王小明" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="身分證字號" prop="idNumber">
              <el-input v-model="userForm.idNumber" placeholder="身分證字號 (10碼)" maxlength="10" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="薪轉戶銀行帳號" prop="bankAccount">
              <el-input v-model="userForm.bankAccount" placeholder="請輸入匯款銀行帳號" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所屬部門" prop="deptId">
              <el-select v-model="userForm.deptId" placeholder="請選擇部門" style="width: 100%">
                <el-option 
                  v-for="dept in departments" 
                  :key="dept.deptId" 
                  :label="dept.deptName" 
                  :value="dept.deptId" 
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="職位名稱" prop="position">
              <el-input v-model="userForm.position" placeholder="例如: 資深工程師" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="權限角色" prop="role">
              <el-select v-model="userForm.role" style="width: 100%">
                <el-option label="普通職員 (EMPLOYEE)" value="EMPLOYEE" />
                <el-option label="部門主管 (MANAGER)" value="MANAGER" />
                <el-option label="系統管理員 (ADMIN)" value="ADMIN" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="預設直屬主管">
              <el-select v-model="userForm.managerId" placeholder="請設定直屬主管" style="width: 100%" clearable>
                <el-option 
                  v-for="mgr in potentialManagers" 
                  :key="mgr.userId" 
                  :label="`${mgr.realName} (${mgr.userId}) - ${mgr.position}`" 
                  :value="mgr.userId" 
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="userDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="saveUser">儲存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 密碼變更彈窗 -->
    <el-dialog v-model="pwdDialogVisible" title="變更員工密碼" width="440px" align-center>
      <el-form :model="pwdForm" :rules="pwdRules" ref="pwdFormRef" label-position="top">
        <div class="pwd-notice">正在為帳號 <strong>{{ pwdForm.userId }}</strong> 變更密碼。</div>
        <el-form-item label="輸入新密碼" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" placeholder="請輸入新登入密碼" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="pwdDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="savePassword">確認變更</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.user-manage-wrapper {
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

.password-notice {
  display: flex;
  align-items: center;
  margin-top: 36px;
  font-size: 13px;
  color: var(--text-muted);
}

.pwd-notice {
  background-color: #f8fafc;
  padding: 12px;
  border-radius: var(--radius-sm);
  margin-bottom: 16px;
  font-size: 13px;
  border: 1px solid var(--border-color);
}
</style>
