import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  // ===== 求职端（免登录，自动创建演示账号） =====
  {
    path: '/seeker/upload',
    name: 'SeekerUpload',
    component: () => import('../views/seeker/UploadView.vue')
  },
  {
    path: '/seeker/status',
    name: 'SeekerStatus',
    component: () => import('../views/seeker/StatusView.vue')
  },
  {
    path: '/seeker/apply/:position',
    name: 'SeekerApply',
    component: () => import('../views/seeker/ApplyView.vue')
  },
  { path: '/seeker', redirect: '/seeker/upload' },

  // ===== HR 管理端 =====
  {
    path: '/hr',
    name: 'HrDashboard',
    component: () => import('../views/hr/DashboardView.vue')
  },
  {
    path: '/hr/positions/:position/candidates',
    name: 'HrCandidateList',
    component: () => import('../views/hr/CandidateListView.vue')
  },
  {
    path: '/hr/candidates/:id',
    name: 'HrCandidateDetail',
    component: () => import('../views/hr/CandidateDetailView.vue')
  },
  {
    path: '/hr/positions/:position/backup',
    name: 'HrBackupList',
    component: () => import('../views/hr/BackupListView.vue')
  },
  {
    path: '/hr/interviews',
    name: 'HrInterviewOverview',
    component: () => import('../views/hr/InterviewOverviewView.vue')
  },
  {
    path: '/hr/interviews/create',
    name: 'HrInterviewForm',
    component: () => import('../views/hr/InterviewFormView.vue')
  },
  {
    path: '/hr/offers/pending',
    name: 'HrPendingOffers',
    component: () => import('../views/hr/PendingOffersView.vue')
  },
  {
    path: '/hr/offers',
    name: 'HrOfferList',
    component: () => import('../views/hr/OfferListView.vue')
  },
  {
    path: '/hr/offers/create/:candidateId',
    name: 'HrOfferForm',
    component: () => import('../views/hr/OfferFormView.vue')
  },
  {
    path: '/hr/onboarding/create/:offerId',
    name: 'HrOnboardingForm',
    component: () => import('../views/hr/OnboardingFormView.vue')
  },
  {
    path: '/hr/positions/manage',
    name: 'HrPositionManage',
    component: () => import('../views/hr/PositionManageView.vue')
  },

  // 默认重定向到求职端
  { path: '/', redirect: '/seeker/upload' },
  { path: '/:pathMatch(.*)*', redirect: '/seeker/upload' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
