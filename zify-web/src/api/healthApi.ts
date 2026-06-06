import { apiGet } from './request'

export const getHealth = () => apiGet<string>('/health')
