import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 响应拦截：统一处理错误
http.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message))
    }
    return res.data
  },
  error => {
    const msg = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

// ===== 求职端 API =====
export const seekerApi = {
  login(params) { return http.post('/seeker/login', null, { params }) },
  getStatus(id) { return http.get(`/seeker/${id}/status`) },
  canSubmit(id) { return http.get(`/seeker/${id}/can-submit`) },
  uploadResume(formData) { return http.post('/resume/upload', formData) },
  respondInterview(id, accept) { return http.put(`/interview/${id}/respond`, null, { params: { accept } }) },
  respondOffer(id, accept) { return http.put(`/offer/${id}/respond`, null, { params: { accept } }) }
}

// ===== HR 端 API =====
export const hrApi = {
  getPositions() { return http.get('/hr/positions') },
  getPositionStats() { return http.get('/hr/positions/stats') },
  getCandidates(position, params) { return http.get(`/hr/positions/${position}/candidates`, { params }) },
  getCandidate(id) { return http.get(`/hr/candidates/${id}`) },
  updateStatus(id, params) { return http.put(`/hr/candidates/${id}/status`, null, { params }) },
  aiQualify(id) { return http.post(`/hr/candidates/${id}/ai-qualify`) },
  getBackupList(position) { return http.get(`/hr/positions/${position}/backup`) },
  restoreCandidate(id) { return http.put(`/hr/candidates/${id}/restore`) },
  getInterviews() { return http.get('/hr/interviews') },
  createInterview(data) { return http.post('/hr/interviews', data) },
  getCandidateInterviews(candidateId) { return http.get(`/hr/candidates/${candidateId}/interviews`) },
  getPendingOffers() { return http.get('/hr/offers/pending') },
  createOffer(candidateId, data) { return http.post('/hr/offers', data, { params: { candidateId } }) },
  getOffers() { return http.get('/hr/offers') },
  getCandidateOffer(candidateId) { return http.get(`/hr/candidates/${candidateId}/offer`) },
  createOnboarding(offerId, data) { return http.post('/hr/onboarding', data, { params: { offerId } }) },
  getCandidateOnboarding(candidateId) { return http.get(`/hr/candidates/${candidateId}/onboarding`) }
}

export default http
