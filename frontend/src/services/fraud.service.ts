import { apiClient } from '../api/axios'
import type { ApiResponse } from '../types/api.types'
import type {
    AiFraudAnalysis,
    DashboardSummary,
    FraudDetail,
    FraudFilters,
    FraudSummary,
    Page,
    ReviewStatus,
    RuleHit,
    TransactionGraph,
} from '../types/fraud.types'

export async function getAiAnalysis(transactionRef: string): Promise<AiFraudAnalysis> {
    return (
        await apiClient.get<ApiResponse<AiFraudAnalysis>>(
            `/transactions/${encodeURIComponent(transactionRef)}/ai-analysis`,
        )
    ).data.data
}

export async function createAiAnalysis(transactionRef: string): Promise<AiFraudAnalysis> {
    return (
        await apiClient.post<ApiResponse<AiFraudAnalysis>>(
            `/transactions/${encodeURIComponent(transactionRef)}/ai-analysis`,
            undefined,
            { timeout: 120_000 },
        )
    ).data.data
}

export async function getFraudTransactions(filters: FraudFilters): Promise<Page<FraudSummary>> {
    const response = await apiClient.get<ApiResponse<Page<FraudSummary>>>('/fraud-transactions', { params: filters })
    return response.data.data
}
export async function getSummary(period: Pick<FraudFilters, 'from' | 'to'> = {}): Promise<DashboardSummary> {
    return (await apiClient.get<ApiResponse<DashboardSummary>>('/fraud-transactions/summary', { params: period })).data
        .data
}
export async function getDetail(id: string): Promise<FraudDetail> {
    return (await apiClient.get<ApiResponse<FraudDetail>>(`/fraud-transactions/${encodeURIComponent(id)}`)).data.data
}
export async function getGraph(id: string): Promise<TransactionGraph> {
    return (await apiClient.get<ApiResponse<TransactionGraph>>(`/fraud-transactions/${encodeURIComponent(id)}/graph`))
        .data.data
}
export async function getRuleHits(id: string): Promise<RuleHit[]> {
    return (await apiClient.get<ApiResponse<RuleHit[]>>(`/fraud-transactions/${encodeURIComponent(id)}/rule-hits`)).data
        .data
}
export async function reviewCase(
    id: string,
    status: ReviewStatus,
): Promise<{ id: string; reviewStatus: ReviewStatus }> {
    return (
        await apiClient.patch<ApiResponse<{ id: string; reviewStatus: ReviewStatus }>>(
            `/fraud-transactions/${encodeURIComponent(id)}/review-status`,
            { status },
        )
    ).data.data
}
