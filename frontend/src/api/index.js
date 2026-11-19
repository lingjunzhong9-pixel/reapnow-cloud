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

    // 401 的两种含义必须区分开，否则游客会被莫名弹到登录页：
    //   A. 本来带着 token，但 token 过期/无效 → 清登录态并跳登录页
    //   B. 本来就没登录（游客），打到了需要登录的接口 → 静默失败，不跳转
    // 另外 config.silent401 = true 的请求（后台可选项，如行情、抢单记录）
    // 即使过期也不跳转，由调用方决定怎么降级。
    if (resp && resp.status === 401) {
      const hadToken = Boolean(err.config && err.config.headers && err.config.headers['X-Token'])
      const silent = Boolean(err.config && err.config.silent401)
      if (hadToken && !silent) {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        window.location.hash = '#/login'
      }
    }

    let msg = (body && body.message) || err.message || '请求失败'
    if (err.message === 'Network Error') {
      msg = '无法连接后端，请确认网关（8080）已启动'
    }
    return Promise.reject(new Error(msg))
  }
)

// ===================================================================
//  按业务域封装接口
//  视图层只调用这里的函数，不直接拼 URL —— 后端改路径时只改这一处
// ===================================================================

export const authApi = {
  login: (username, password) => api.post('/auth/login', { username, password }),
  register: (payload) => api.post('/auth/register', payload),
  logout: () => api.post('/auth/logout'),
  verify: () => api.get('/auth/verify'),
}

export const demandApi = {
  /** 抢单大厅（免登录）；urgent=true 只看临期加急单 */
  list: (params) => api.get('/demands', { params }),
  /** 我发布的需求 */
  mine: () => api.get('/demands/mine'),
  byId: (id) => api.get(`/demands/${id}`),
  create: (payload) => api.post('/demands', payload),
  /** 手动触发临期巡检（仅 DISPATCHER / ADMIN） */
  scanUrgent: () => api.post('/demands/scan-urgent'),
  /** 配置中心演示 */
  config: () => api.get('/config/demo'),
}

export const orderApi = {
  grab: (payload) => api.post('/orders/grab', payload),
  mine: () => api.get('/orders/mine'),
  /** 某块地被谁抢了：游客点「详情」时也会请求，401 静默降级，不跳登录页 */
  byDemand: (demandId) => api.get(`/orders/by-demand/${demandId}`, { silent401: true }),
  byId: (id) => api.get(`/orders/${id}`),
  cancel: (id) => api.post(`/orders/${id}/cancel`),
  /**
   * C03-6 行情参考价（免登录）：近 30 天成交单价的中位数与四分位。
   * @param {string} [region] 传了就只看该地区成交，不传为全平台
   */
  marketPrice: (region) =>
    api.get('/orders/market-price', { params: { region }, silent401: true }),
  /** C03-7 调度员兜底指派 */
  assign: (payload) => api.post('/orders/assign', payload),
}

export const userApi = {
  /**
   * 全量用户（仅 ADMIN / DISPATCHER）。
   * 后端已做角色校验，且不返回手机号 —— 只给工作台筛选机手用。
   */
  list: () => api.get('/users'),
}

// -------------------------------------------------------------------
//  支付 / 定金流水
//  对应后端 C04-1 ~ C04-4 四个接口
// -------------------------------------------------------------------
export const paymentApi = {
  /** C04-1 支付定金 */
  payDeposit: (orderId, channel = 'MOCK') =>
    api.post(`/payments/${orderId}/pay-deposit`, null, { params: { channel } }),

  /**
   * C04-3 按订单查定金流水
   * 农户在「谁抢了」、机手在「我的接单」都需要看到这笔定金付没付、退没退
   */
  byOrder: (orderId) => api.get(`/payments/by-order/${orderId}`),

  /**
   * C04-4 后台定金流水列表（仅 ADMIN / DISPATCHER）
   * @param {{status?: string, page?: number, size?: number}} opts
   */
  list: ({ status, page = 1, size = 10 } = {}) =>
    api.get('/payments', { params: { status: status || undefined, page, size } }),

  /**
   * C04-5 后台流水详情 —— 含关联订单与交易双方（仅 ADMIN / DISPATCHER）
   */
  detail: (id) => api.get(`/payments/${id}`),
}

export default api
