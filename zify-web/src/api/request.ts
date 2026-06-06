import axios from 'axios'

const request = axios.create({
  baseURL: '',
  timeout: 10000,
})

request.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data
    if (code === 200) {
      return data
    }
    return Promise.reject(new Error(message || '请求失败'))
  },
  (error) => {
    return Promise.reject(error)
  },
)

export default request
