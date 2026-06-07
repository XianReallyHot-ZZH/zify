import { apiGet, apiPost, apiPut, apiDelete } from './request'
import type { OffsetPageResponse } from '../types/api'

// ─── Provider Types ────────────────────────────────────────

type CreateProviderRequest = {
  name: string
  providerType: string
  apiKey?: string
  baseUrl: string
  extraConfig?: Record<string, unknown>
}

type UpdateProviderRequest = {
  name?: string
  apiKey?: string
  baseUrl?: string
  extraConfig?: Record<string, unknown>
}

type ProviderListQuery = {
  page?: number
  pageSize?: number
  providerType?: string
  status?: string
}

type ProviderResponse = {
  id: string
  name: string
  providerType: string
  baseUrl: string
  extraConfig: Record<string, unknown> | null
  status: string
  hasApiKey: boolean
  modelCount: number
  createdAt: string
  updatedAt: string
}

type ProviderTestResult = {
  success: boolean
  message: string
  latencyMs: number
  availableModels: string[] | null
}

// ─── Model Types ───────────────────────────────────────────

type CreateModelRequest = {
  modelName: string
  displayName?: string
  modelType: string
  enabled?: boolean
}

type UpdateModelRequest = {
  displayName?: string
  modelType?: string
  enabled?: boolean
  defaultParams?: Record<string, unknown>
}

type ModelListQuery = {
  page?: number
  pageSize?: number
  modelType?: string
  enabled?: boolean | string
  providerId?: string
}

type ModelResponse = {
  id: string
  providerId: string
  modelName: string
  displayName: string | null
  modelType: string
  enabled: boolean
  defaultParams: Record<string, unknown> | null
  providerName: string
  providerType: string
  providerStatus: string
  createdAt: string
  updatedAt: string
}

type ModelTestResult = {
  success: boolean
  message: string
  latencyMs: number
  errorDetail: string | null
}

// ─── Provider API ──────────────────────────────────────────

function createProvider(data: CreateProviderRequest): Promise<ProviderResponse> {
  return apiPost('/model/providers', data)
}

function listProviders(query?: ProviderListQuery): Promise<OffsetPageResponse<ProviderResponse>> {
  return apiGet('/model/providers', query as Record<string, unknown>)
}

function getProvider(id: string): Promise<ProviderResponse> {
  return apiGet(`/model/providers/${id}`)
}

function updateProvider(id: string, data: UpdateProviderRequest): Promise<ProviderResponse> {
  return apiPut(`/model/providers/${id}`, data)
}

function deleteProvider(id: string): Promise<void> {
  return apiDelete(`/model/providers/${id}`)
}

function updateProviderStatus(id: string, status: string): Promise<void> {
  return apiPut(`/model/providers/${id}/status`, { status })
}

function testProvider(id: string): Promise<ProviderTestResult> {
  return apiPost(`/model/providers/${id}/test`)
}

// ─── Model API ─────────────────────────────────────────────

function createModel(providerId: string, data: CreateModelRequest): Promise<ModelResponse> {
  return apiPost(`/model/providers/${providerId}/models`, data)
}

function listModels(query?: ModelListQuery): Promise<OffsetPageResponse<ModelResponse>> {
  return apiGet('/model/models', query as Record<string, unknown>)
}

function listProviderModels(providerId: string): Promise<ModelResponse[]> {
  return apiGet(`/model/providers/${providerId}/models`)
}

function getModel(id: string): Promise<ModelResponse> {
  return apiGet(`/model/models/${id}`)
}

function updateModel(id: string, data: UpdateModelRequest): Promise<ModelResponse> {
  return apiPut(`/model/models/${id}`, data)
}

function deleteModel(id: string): Promise<void> {
  return apiDelete(`/model/models/${id}`)
}

function updateModelEnabled(id: string, enabled: boolean): Promise<void> {
  return apiPut(`/model/models/${id}/enabled`, { enabled })
}

function testModel(id: string): Promise<ModelTestResult> {
  return apiPost(`/model/models/${id}/test`)
}

export type {
  CreateProviderRequest,
  UpdateProviderRequest,
  ProviderListQuery,
  ProviderResponse,
  ProviderTestResult,
  CreateModelRequest,
  UpdateModelRequest,
  ModelListQuery,
  ModelResponse,
  ModelTestResult,
}

export {
  createProvider,
  listProviders,
  getProvider,
  updateProvider,
  deleteProvider,
  updateProviderStatus,
  testProvider,
  createModel,
  listModels,
  listProviderModels,
  getModel,
  updateModel,
  deleteModel,
  updateModelEnabled,
  testModel,
}
