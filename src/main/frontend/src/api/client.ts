import axios from 'axios'

const api = axios.create({ baseURL: '' })

export type FileType = 'CODA' | 'SWIFT'
export type Library = 'BEANIO' | 'FIXFORMAT4J' | 'FIXEDLENGTH' | 'BINDY'

export interface GenerateDomainResponse {
  operationId: number
  accountsGenerated: number
  transactionsGenerated: number
  timestamp: string
}

export interface BatchJobRequest {
  fileType: FileType
  library: Library
}

export interface BatchJobResponse {
  jobExecutionId: number
  fileType: FileType
  library: Library
  status: string
  fileContent: string
  fileName: string
  timestamp: string
}

export interface BatchHistoryResponse {
  jobExecutionId: number
  fileType: FileType
  library: Library
  status: string
  durationMs: number
  startTime: string | null
  endTime: string | null
}

export interface BenchmarkResultResponse {
  id: number
  jobExecutionId: number
  fileType: FileType
  library: Library
  throughputRps: number
  generationDurationMs: number
  parseDurationMs: number
  batchDurationMs: number
  memoryUsedBytes: number
  successRate: number
  symmetryRate: number
  timestamp: string
}

export const generateDomain = () =>
  api.post<GenerateDomainResponse>('/api/domain/generate').then(r => r.data)

export const generateBatch = (req: BatchJobRequest) =>
  api.post<BatchJobResponse>('/api/batch/generate', req).then(r => r.data)

export const getBatchHistory = () =>
  api.get<BatchHistoryResponse[]>('/api/batch/history').then(r => r.data)

export const getBenchmarkResults = () =>
  api.get<BenchmarkResultResponse[]>('/api/benchmark/results').then(r => r.data)

export const getHealth = () =>
  api.get('/actuator/health').then(r => r.data)

export const getInfo = () =>
  api.get('/actuator/info').then(r => r.data)
