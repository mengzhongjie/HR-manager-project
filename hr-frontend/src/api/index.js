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
  demoLogin() { return http.post('/seeker/demo') },
  getStatus(id) { return http.get(`/seeker/${id}/status`) },
  canSubmit(id) { return http.get(`/seeker/${id}/can-submit`) },
  respondInterview(id, accept) { return http.put(`/interview/${id}/respond`, null, { params: { accept } }) },
  respondOffer(id, accept) { return http.put(`/offer/${id}/respond`, null, { params: { accept } }) },
  updateCandidate(candidateId, data) { return http.put(`/seeker/candidate/${candidateId}`, data) },
  // 同步解析 + 提交
  parseResumeSync(formData) { return http.post('/resume/parse-sync', formData) },
  submitCandidate(data) { return http.post('/resume/submit', data) }
}

// ===== HR 端 API =====
export const hrApi = {
  getPositions() { return http.get('/hr/positions') },
  getPositionStats() { return http.get('/hr/positions/stats') },
  getCandidates(position, params) { return http.get(`/hr/positions/${position}/candidates`, { params }) },
  getCandidate(id) { return http.get(`/hr/candidates/${id}`) },
  updateStatus(id, params) { return http.put(`/hr/candidates/${id}/status`, null, { params }) },
  aiQualify(id) { return http.post(`/hr/candidates/${id}/ai-qualify`) },
  inviteInterview(id, params) { return http.post(`/hr/candidates/${id}/invite-interview`, null, { params }) },
  startInterview(id) { return http.put(`/hr/interviews/${id}/start`) },
  completeInterview(id, params) { return http.put(`/hr/interviews/${id}/complete`, null, { params }) },
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
  getCandidateOnboarding(candidateId) { return http.get(`/hr/candidates/${candidateId}/onboarding`) },
  // 岗位CRUD
  getPositionList() { return http.get('/hr/positions/list') },
  createPosition(data) { return http.post('/hr/positions', data) },
  updatePosition(id, data) { return http.put(`/hr/positions/${id}`, data) },
  deletePosition(id) { return http.delete(`/hr/positions/${id}`) }
}

// SSE 岗位变更事件订阅
export function subscribePositionEvents(onEvent) {
  const source = new EventSource('/api/hr/positions/events')
  source.addEventListener('position-change', (event) => {
    try {
      if (onEvent) onEvent(JSON.parse(event.data))
    } catch (e) { /* ignore parse errors */ }
  })
  source.addEventListener('connected', () => {
    console.log('SSE connected for position events')
  })
  source.onerror = () => { /* EventSource auto-reconnects */ }
  return () => source.close()
}

export default http
