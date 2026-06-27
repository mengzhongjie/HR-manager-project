import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  // ===== 求职端 =====
  {
    path: '/seeker/login',
    name: 'SeekerLogin',
    component: () => import('../views/seeker/LoginView.vue')
  },
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
  // 求职端重定向
  { path: '/seeker', redirect: '/seeker/login' },

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

  // 默认重定向
  { path: '/', redirect: '/seeker/login' },
  { path: '/:pathMatch(.*)*', redirect: '/seeker/login' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
