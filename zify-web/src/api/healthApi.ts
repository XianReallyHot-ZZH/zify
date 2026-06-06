import request from './request'

export const getHealth = () => request.get<never, string>('/api/health')
