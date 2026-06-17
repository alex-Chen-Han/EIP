import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import FormSubmit from '../views/FormSubmit.vue'
import AdminUserManage from '../views/AdminUserManage.vue'
import AdminAuditLogs from '../views/AdminAuditLogs.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: Login
  },
  {
    path: '/',
    name: 'Dashboard',
    component: Dashboard,
    meta: { requiresAuth: true }
  },
  {
    path: '/form-submit',
    name: 'FormSubmit',
    component: FormSubmit,
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/users',
    name: 'AdminUserManage',
    component: AdminUserManage,
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/audit-logs',
    name: 'AdminAuditLogs',
    component: AdminAuditLogs,
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const currentUser = JSON.parse(localStorage.getItem('currentUser') || 'null')

  if (to.meta.requiresAuth && !currentUser) {
    next({ name: 'Login' })
  } else if (to.meta.requiresAdmin && currentUser?.role !== 'ADMIN') {
    next({ name: 'Dashboard' })
  } else {
    next()
  }
})

export default router
