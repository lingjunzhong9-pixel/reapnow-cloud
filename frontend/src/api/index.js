import axios from 'axios'

const api = axios.create({ baseURL: '/api', timeout: 10000 })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['X-Token'] = token
  }
  return config
})

api.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code !== 'undefined' && body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body ? body.data : body
  },
  (err) => {
    const resp = err.response
    const body = resp && resp.data
    if (resp && resp.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      window.location.hash = '#/login'
    }
    let msg = (body && body.message) || err.message || '请求失败'
    if (err.message === 'Network Error') {
      msg = '无法连接后端，请确认后端已启动（8081）'
    }
    return Promise.reject(new Error(msg))
  }
)

export default api
