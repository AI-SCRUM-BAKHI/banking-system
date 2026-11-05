import { afterEach, expect, it, vi } from 'vitest'
import { apiClient } from '../api/axios'
import { createAiAnalysis, getAiAnalysis, getFraudTransactions } from './fraud.service'

afterEach(() => vi.restoreAllMocks())
it('encodes transaction refs and gives AI generation a longer timeout', async () => {
    const data = { fraudExplanation: { summary: 'Analysis', reasons: [] }, investigationRecommendations: [] }
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { data } })
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ data: { data } })
    expect(await getAiAnalysis('TX/A B')).toEqual(data)
    expect(await createAiAnalysis('TX/A B')).toEqual(data)
    expect(get).toHaveBeenCalledWith('/transactions/TX%2FA%20B/ai-analysis')
    expect(post).toHaveBeenCalledWith('/transactions/TX%2FA%20B/ai-analysis', undefined, { timeout: 120_000 })
})
it('sends supported server pagination and filters and unwraps the backend envelope', async () => {
    const page = { content: [], page: 2, size: 20, totalElements: 43, totalPages: 3 }
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { success: true, data: page } })
    const filters = { page: 2, size: 20, riskLevel: 'HIGH' as const, keyword: 'TX-001', sort: 'riskScore,desc' }
    expect(await getFraudTransactions(filters)).toEqual(page)
    expect(get).toHaveBeenCalledWith('/fraud-transactions', { params: filters })
})
