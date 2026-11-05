import type { ApiResponse } from '../types/api.types'
import type { EvaluationRequest, EvaluationResult } from '../lib/evaluation'
import { apiClient } from '../api/axios'
export async function evaluateTransaction(request: EvaluationRequest): Promise<ApiResponse<EvaluationResult>> {
    return (
        await apiClient.post<ApiResponse<EvaluationResult>>('/transactions/evaluate', request, { baseURL: '/api/v1' })
    ).data
}
