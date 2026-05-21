import axios from 'axios'

const client = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      const message = data?.message || `HTTP ${status}`
      if (status === 409) {
        return Promise.reject(new Error(`状态冲突: ${message}`))
      }
      if (status === 400) {
        return Promise.reject(new Error(`参数错误: ${message}`))
      }
      if (status === 404) {
        return Promise.reject(new Error(`资源不存在: ${message}`))
      }
      return Promise.reject(new Error(message))
    }
    return Promise.reject(new Error('网络错误，请检查后端服务是否启动'))
  }
)

export default client
