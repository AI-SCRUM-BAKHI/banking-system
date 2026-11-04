import { afterEach, expect, it, vi } from 'vitest'
import { apiClient } from '../api/axios'
import { getFraudTransactions } from './fraud.service'

afterEach(() => vi.restoreAllMocks())
it('sends supported server pagination and filters and unwraps the backend envelope', async () => {
    const page = { content: [], page: 2, size: 20, totalElements: 43, totalPages: 3 }
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { success: true, data: page } })
    const filters = { page: 2, size: 20, riskLevel: 'HIGH' as const, keyword: 'TX-001', sort: 'riskScore,desc' }
    expect(await getFraudTransactions(filters)).toEqual(page)
    expect(get).toHaveBeenCalledWith('/fraud-transactions', { params: filters })
})
